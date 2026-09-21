package com.patchcam.app.pipeline

import com.chaquo.python.Python
import com.patchcam.app.models.AstValidationResult
import org.json.JSONObject

/**
 * Stage 5 — Patch validation via Chaquopy (Python only — real Python, on-device)
 * Runs strictly on-device using embedded CPython ast.parse().
 */
object ChaquopyAstValidator {

    fun validate(sourceCode: String, attemptNumber: Int = 1): AstValidationResult {
        return try {
            val py = Python.getInstance()
            val validatorModule = py.getModule("validator")
            val resultJsonString = validatorModule.callAttr("validate_ast", sourceCode).toString()
            val json = JSONObject(resultJsonString)

            val isValid = json.optBoolean("valid", false)
            val error = if (json.has("error") && !json.isNull("error")) json.getString("error") else null
            val line = if (json.has("line") && !json.isNull("line")) json.getInt("line") else null
            val offset = if (json.has("offset") && !json.isNull("offset")) json.getInt("offset") else null
            val sourceLine = if (json.has("source_line") && !json.isNull("source_line")) {
                json.getString("source_line")
            } else {
                null
            }

            AstValidationResult(
                valid = isValid,
                errorMessage = error,
                errorLine = line,
                errorOffset = offset,
                sourceLine = sourceLine,
                attemptNumber = attemptNumber
            )
        } catch (e: Exception) {
            AstValidationResult(
                valid = false,
                errorMessage = "Chaquopy execution error: ${e.localizedMessage}",
                attemptNumber = attemptNumber
            )
        }
    }
}
