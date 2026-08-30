package com.kairo.reader.data.annotations

import com.kairo.reader.core.model.BookId
import com.kairo.reader.core.model.SavedAnnotation
import com.kairo.reader.core.model.SavedAnnotationItem
import com.kairo.reader.core.model.requireValidForStorage
import com.kairo.reader.data.local.SavedAnnotationDao
import com.kairo.reader.data.local.toDomain
import com.kairo.reader.data.local.toEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SavedAnnotationRepositoryImpl(private val annotationDao: SavedAnnotationDao,) : SavedAnnotationRepository {
    override fun observeAnnotations(): Flow<List<SavedAnnotationItem>> =
        annotationDao.observeWithBook().map { items ->
            items.map { it.toDomain() }.sortedByDescending { it.annotation.updatedAt }
        }

    override fun observeForBook(bookId: BookId): Flow<List<SavedAnnotation>> =
        annotationDao.observeForBook(bookId.value).map { items -> items.map { it.toDomain() } }

    override suspend fun save(annotation: SavedAnnotation): Boolean =
        annotationDao.upsert(annotation.requireValidForStorage().toEntity())

    override suspend fun delete(annotationId: String) {
        annotationDao.delete(annotationId)
    }

    override suspend fun deleteForBook(bookId: BookId) {
        annotationDao.deleteForBook(bookId.value)
    }
}
