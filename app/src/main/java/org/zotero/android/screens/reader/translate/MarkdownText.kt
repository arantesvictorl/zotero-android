package org.zotero.android.screens.reader.translate

import android.content.Context
import android.util.TypedValue
import android.widget.TextView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.Markwon
import io.noties.markwon.ext.latex.JLatexMathPlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.inlineparser.MarkwonInlineParserPlugin

/**
 * Renders Markdown with LaTeX formulas ($...$, $$...$$, \(...\), \[...\]) as selectable text.
 */
@Composable
internal fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val textStyle = MaterialTheme.typography.bodyLarge
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    val textSizePx = with(LocalDensity.current) { textStyle.fontSize.toPx() }

    val markwon = remember(context, textColor, textSizePx) {
        createMarkwon(context = context, textColor = textColor, textSizePx = textSizePx)
    }
    val normalized = remember(markdown) { MarkdownMathNormalizer.normalize(markdown) }

    AndroidView(
        modifier = modifier,
        factory = { viewContext ->
            TextView(viewContext).apply {
                setTextIsSelectable(true)
            }
        },
        update = { textView ->
            textView.setTextColor(textColor)
            textView.setLinkTextColor(linkColor)
            textView.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSizePx)
            textView.setLineSpacing(0f, 1.15f)
            markwon.setMarkdown(textView, normalized)
        },
    )
}

private fun createMarkwon(context: Context, textColor: Int, textSizePx: Float): Markwon {
    return Markwon.builder(context)
        .usePlugin(MarkwonInlineParserPlugin.create())
        .usePlugin(JLatexMathPlugin.create(textSizePx) { builder ->
            builder.inlinesEnabled(true)
            builder.theme().textColor(textColor)
        })
        .usePlugin(TablePlugin.create(context))
        .usePlugin(StrikethroughPlugin.create())
        .build()
}
