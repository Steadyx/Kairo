package com.kairo.reader.data.books

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.kairo.reader.core.dispatchers.DispatcherProvider
import com.kairo.reader.core.model.Book
import com.kairo.reader.core.model.BookId
import com.kairo.reader.data.books.mobi.MobiContentProcessor
import com.kairo.reader.data.books.mobi.MobiFallbackParser
import com.kairo.reader.data.books.mobi.MobiFormatValidator
import com.kairo.reader.data.books.mobi.MobiLimits
import com.kairo.reader.data.books.mobi.MobiParserEngine
import com.kairo.reader.data.books.mobi.MobiTextLimitException
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class MobiBookParser(private val dispatcherProvider: DispatcherProvider,) : BookParser {
    private val fallbackParser = MobiFallbackParser()

    override suspend fun parse(
        context: Context,
        uri: Uri,
        bookId: BookId,
    ): Book = parse(context, uri, bookId, sourceDisplayName = null)

    override suspend fun parse(
        context: Context,
        uri: Uri,
        bookId: BookId,
        sourceDisplayName: String?,
    ): Book =
        withContext(dispatcherProvider.io) {
            val fileName = sourceDisplayName?.takeIf { it.isNotBlank() }
                ?: uri.lastPathSegment
                ?: "book.mobi"

            val fileSize = resolveFileSize(context, uri)
            require(fileSize < 0 || !isFileTooLarge(fileSize)) {
                "MOBI file too large (max ${MobiLimits.MAX_FILE_SIZE_BYTES / BYTES_PER_KIB / BYTES_PER_KIB}MB)"
            }

            val importContext = coroutineContext
            val checkActive = { importContext.ensureActive() }
            val data =
                requireNotNull(context.contentResolver.openInputStream(uri)) {
                    "Unable to read MOBI file"
                }.use { input ->
                    readInputBytesWithLimit(BufferedInputStream(input), MobiLimits.MAX_FILE_SIZE_BYTES, checkActive)
                }

            MobiFormatValidator.validate(data)

            val parserEngine = MobiParserEngine(contentProcessor = MobiContentProcessor(checkActive))
            try {
                parserEngine.parse(
                    context = context,
                    bookId = bookId,
                    data = data,
                    fallbackFileName = fileName,
                )
            } catch (failure: MobiTextLimitException) {
                throw failure
            } catch (_: IllegalArgumentException) {
                checkActive()
                fallbackParser.parse(
                    bookId = bookId,
                    data = data,
                    fileName = fileName,
                )
            }
        }

    override fun supports(extension: String): Boolean =
        extension.trim().lowercase(Locale.ROOT) in BookImportFormats.mobiFamilyExtensions

    private fun resolveFileSize(
        context: Context,
        uri: Uri,
    ): Long {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst() && index >= 0) {
                    return cursor.getLong(index)
                }
            }
        }
        runCatching {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                return descriptor.statSize
            }
        }
        return -1L
    }

    private fun isFileTooLarge(fileSize: Long): Boolean =
        fileSize > MobiLimits.MAX_FILE_SIZE_BYTES

    private fun readInputBytesWithLimit(
        input: InputStream,
        maxBytes: Long,
        checkActive: () -> Unit,
    ): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L

        while (true) {
            checkActive()
            val read = input.read(buffer)
            if (read == -1) break
            if (read == 0) continue
            total += read
            require(total <= maxBytes) {
                "MOBI file too large (max ${maxBytes / BYTES_PER_KIB / BYTES_PER_KIB}MB)"
            }
            output.write(buffer, 0, read)
        }

        return output.toByteArray()
    }
}

private const val BYTES_PER_KIB = 1024
