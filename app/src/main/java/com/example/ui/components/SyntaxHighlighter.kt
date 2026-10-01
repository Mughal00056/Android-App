package com.example.ui.components

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.example.ui.theme.SyntaxAnnotation
import com.example.ui.theme.SyntaxComment
import com.example.ui.theme.SyntaxFunction
import com.example.ui.theme.SyntaxKeyword
import com.example.ui.theme.SyntaxNumber
import com.example.ui.theme.SyntaxString
import com.example.ui.theme.SyntaxType

class CodeSyntaxTransformation(private val extension: String = "kt") : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(
            text = highlightCode(text.text, extension),
            offsetMapping = OffsetMapping.Identity
        )
    }
}

private val KEYWORD_REGEX = Regex(
    """\b(package|import|class|interface|object|fun|val|var|if|else|when|for|while|do|return|break|continue|public|private|protected|internal|static|final|void|int|long|double|float|boolean|char|auto|const|struct|namespace|using|include|true|false|null|suspend|override|data|sealed|in|until)\b"""
)

private val TYPE_REGEX = Regex(
    """\b(String|Int|Long|Double|Float|Boolean|List|Map|Set|Array|File|Context|Bundle|System|Math|std|cout|endl|JNIEnv|jobject|jstring)\b"""
)

private val FUNCTION_REGEX = Regex(
    """\b([a-zA-Z_][a-zA-Z0-9_]*)\s*(?=\()"""
)

private val NUMBER_REGEX = Regex(
    """\b\d+(\.\d+)?[Lfd]?\b"""
)

private val ANNOTATION_REGEX = Regex(
    """@[a-zA-Z_][a-zA-Z0-9_]*|#include|#pragma|#ifndef|#define|#endif"""
)

private val STRING_REGEX = Regex(
    """"[^"\n]*""""
)

private val COMMENT_REGEX = Regex(
    """//.*$|/\*[\s\S]*?\*/|^#.*$""",
    setOf(RegexOption.MULTILINE)
)

fun highlightCode(code: String, ext: String = "kt"): AnnotatedString {
    return buildAnnotatedString {
        append(code)

        // 1. Types
        TYPE_REGEX.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = SyntaxType, fontWeight = FontWeight.Medium),
                match.range.first,
                match.range.last + 1
            )
        }

        // 2. Functions
        FUNCTION_REGEX.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = SyntaxFunction),
                match.range.first,
                match.range.last + 1
            )
        }

        // 3. Numbers
        NUMBER_REGEX.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = SyntaxNumber),
                match.range.first,
                match.range.last + 1
            )
        }

        // 4. Keywords
        KEYWORD_REGEX.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = SyntaxKeyword, fontWeight = FontWeight.Bold),
                match.range.first,
                match.range.last + 1
            )
        }

        // 5. Annotations & Preprocessor directives
        ANNOTATION_REGEX.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = SyntaxAnnotation, fontWeight = FontWeight.SemiBold),
                match.range.first,
                match.range.last + 1
            )
        }

        // 6. Strings (overrides keywords inside quotes)
        STRING_REGEX.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = SyntaxString),
                match.range.first,
                match.range.last + 1
            )
        }

        // 7. Comments (overrides everything inside comments)
        COMMENT_REGEX.findAll(code).forEach { match ->
            addStyle(
                SpanStyle(color = SyntaxComment),
                match.range.first,
                match.range.last + 1
            )
        }
    }
}
