package com.routeprice.feature.quote

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.routeprice.core.domain.model.TripQuote
import com.routeprice.core.domain.util.BrFormat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class ShareFormat { WHATSAPP, IMAGE, PDF, TEXT }

/** O que a interface precisa para abrir o compartilhamento. */
data class SharePayload(
    val format: ShareFormat,
    val text: String,
    val fileUri: String? = null,
    val mimeType: String = "text/plain",
)

interface QuoteSharer {
    suspend fun prepare(quote: TripQuote, format: ShareFormat): SharePayload
}

class AndroidQuoteSharer @Inject constructor(@ApplicationContext private val context: Context) : QuoteSharer {

    override suspend fun prepare(quote: TripQuote, format: ShareFormat): SharePayload = withContext(Dispatchers.IO) {
        val text = PassengerView.text(quote)
        when (format) {
            ShareFormat.TEXT -> SharePayload(format, text)
            ShareFormat.WHATSAPP, ShareFormat.IMAGE -> {
                val file = outFile(quote, "png")
                val bitmap = QuoteDocumentRenderer(context).renderBitmap(quote)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
                bitmap.recycle()
                SharePayload(format, text, uri(file).toString(), "image/png")
            }
            ShareFormat.PDF -> {
                val file = outFile(quote, "pdf")
                file.outputStream().use { QuoteDocumentRenderer(context).writePdf(quote, it) }
                SharePayload(format, text, uri(file).toString(), "application/pdf")
            }
        }
    }

    private fun outFile(quote: TripQuote, ext: String): File {
        val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
        // Limpa arquivos antigos: o compartilhamento é efêmero e não deve acumular dados pessoais.
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > MAX_AGE_MS }?.forEach { it.delete() }
        val date = BrFormat.date(quote.createdAtMillis).replace("/", "-")
        return File(dir, "orcamento-$date-${quote.id.take(SHORT_ID)}.$ext")
    }

    private fun uri(file: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    private companion object {
        const val SHARE_DIR = "shared"
        const val PNG_QUALITY = 100
        const val SHORT_ID = 8
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class QuoteShareModule {
    @Binds abstract fun sharer(impl: AndroidQuoteSharer): QuoteSharer
}

/** Abre o WhatsApp (ou o seletor do Android) com o conteúdo preparado. */
object ShareLauncher {
    private val whatsappPackages = listOf("com.whatsapp", "com.whatsapp.w4b")

    fun launch(context: Context, payload: SharePayload) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = payload.mimeType
            putExtra(Intent.EXTRA_TEXT, payload.text)
            payload.fileUri?.let {
                putExtra(Intent.EXTRA_STREAM, Uri.parse(it))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        if (payload.format == ShareFormat.WHATSAPP) {
            for (pkg in whatsappPackages) {
                try {
                    context.startActivity(Intent(intent).setPackage(pkg))
                    return
                } catch (_: ActivityNotFoundException) {
                    // tenta o próximo; se nenhum existir, cai no seletor padrão
                }
            }
        }
        context.startActivity(Intent.createChooser(intent, "Enviar orçamento"))
    }
}
