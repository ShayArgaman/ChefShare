package com.chefshare.app.data.repository

import android.net.Uri
import com.chefshare.app.data.FirestorePaths
import com.chefshare.app.data.model.Recipe
import com.chefshare.app.util.Resource
import com.chefshare.app.util.toUserMessage
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Repository for recipes: Cloud Firestore for the documents, Cloud Storage for the photos.
 *
 * Design notes:
 *  - The feed uses a LIVE Firestore snapshot listener (no offline cache / no Room) so the UI updates
 *    in real time when anyone adds a recipe or toggles a like.
 *  - Every call is wrapped (try/catch or the listener's error param) and returns a [Resource].
 *  - Dependencies are injected with defaults so ViewModels can call `RecipeRepository()`.
 */
class RecipeRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance()
) {

    // Shortcut to the "recipes" collection.
    private val recipesRef = firestore.collection(FirestorePaths.COLLECTION_RECIPES)

    /**
     * Streams the recipe feed in real time, newest first.
     *
     * `callbackFlow` bridges Firestore's callback-based snapshot listener into a Kotlin Flow:
     *  - emits Loading immediately,
     *  - emits Success(list) on every server/local update,
     *  - emits Error(message) if the listener fails,
     *  - removes the listener automatically when the collector stops (awaitClose) — no leaks.
     */
    fun observeRecipes(): Flow<Resource<List<Recipe>>> = callbackFlow {
        trySend(Resource.Loading)

        val registration = recipesRef
            .orderBy(FirestorePaths.FIELD_TIMESTAMP, Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.toUserMessage()))
                    return@addSnapshotListener
                }
                // toObjects maps each document into a Recipe (id filled via @DocumentId).
                val recipes = snapshot?.toObjects(Recipe::class.java).orEmpty()
                trySend(Resource.Success(recipes))
            }

        // Suspend here until the collector is gone, then detach the listener.
        awaitClose { registration.remove() }
    }

    /** Fetches a single recipe once. */
    suspend fun getRecipe(recipeId: String): Resource<Recipe> = try {
        val document = recipesRef.document(recipeId).get().await()
        val recipe = document.toObject(Recipe::class.java)
        if (recipe != null) Resource.Success(recipe)
        else Resource.Error("Recipe not found.")
    } catch (e: Exception) {
        Resource.Error(e.toUserMessage())
    }

    /**
     * Streams a SINGLE recipe live (used by the detail screen), so its like state stays in sync with
     * the rest of the app in real time. Same snapshot-listener-to-Flow bridge as the feed.
     */
    fun observeRecipe(recipeId: String): Flow<Resource<Recipe>> = callbackFlow {
        trySend(Resource.Loading)
        val registration = recipesRef.document(recipeId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Resource.Error(error.toUserMessage()))
                    return@addSnapshotListener
                }
                val recipe = snapshot?.toObject(Recipe::class.java)
                if (recipe != null) trySend(Resource.Success(recipe))
                else trySend(Resource.Error("Recipe not found."))
            }
        awaitClose { registration.remove() }
    }

    /**
     * Uploads a picked gallery image to Cloud Storage (recipes/{UUID}.jpg) and returns its public
     * download URL. The URL is what we save on the Recipe document (Firestore stores text, not binaries).
     *
     * @param onProgress called with a 0..100 percentage as bytes transfer, so the UI can show progress.
     */
    suspend fun uploadRecipeImage(
        imageUri: Uri,
        onProgress: (Int) -> Unit = {}
    ): Resource<String> = try {
        val fileName = "${UUID.randomUUID()}.jpg"
        val imageRef = storage.reference
            .child(FirestorePaths.STORAGE_RECIPE_IMAGES)
            .child(fileName)

        val uploadTask = imageRef.putFile(imageUri)
        // Report upload progress as a percentage while the bytes stream up.
        uploadTask.addOnProgressListener { snapshot ->
            val percent =
                if (snapshot.totalByteCount > 0)
                    (100 * snapshot.bytesTransferred / snapshot.totalByteCount).toInt()
                else 0
            onProgress(percent)
        }

        uploadTask.await()                                 // suspend until the upload finishes
        val downloadUrl = imageRef.downloadUrl.await()     // then read back the https URL
        Resource.Success(downloadUrl.toString())
    } catch (e: Exception) {
        Resource.Error(e.toUserMessage())
    }

    /** Adds a new recipe document. Firestore generates the document id. */
    suspend fun addRecipe(recipe: Recipe): Resource<Unit> = try {
        recipesRef.add(recipe).await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.toUserMessage())
    }

    /**
     * Adds or removes the user's UID from likedByUsers atomically.
     *
     * arrayUnion / arrayRemove are server-side atomic operations, so concurrent likes from different
     * devices never clobber each other — no read-modify-write race.
     */
    suspend fun toggleLike(
        recipeId: String,
        userId: String,
        currentlyLiked: Boolean
    ): Resource<Unit> = try {
        val change =
            if (currentlyLiked) FieldValue.arrayRemove(userId)
            else FieldValue.arrayUnion(userId)

        recipesRef.document(recipeId)
            .update(FirestorePaths.FIELD_LIKED_BY, change)
            .await()
        Resource.Success(Unit)
    } catch (e: Exception) {
        Resource.Error(e.toUserMessage())
    }
}
