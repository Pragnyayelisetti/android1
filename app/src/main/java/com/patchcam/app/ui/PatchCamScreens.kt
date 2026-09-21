package com.patchcam.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.patchcam.app.models.ChatMessage
import com.patchcam.app.models.ChatRole
import com.patchcam.app.models.Diagnosis
import com.patchcam.app.models.ErrorKind
import com.patchcam.app.models.FeedbackStats
import com.patchcam.app.models.FeedbackVote
import com.patchcam.app.models.HistoryItem
import com.patchcam.app.models.MatchMethod
import com.patchcam.app.models.OcrLineItem
import com.patchcam.app.models.OfficeKitStats
import com.patchcam.app.models.PatchCamThresholds
import com.patchcam.app.models.ScreenStep
import com.patchcam.app.pipeline.FixFeedbackStore
import com.patchcam.app.pipeline.KnowledgeBase
import com.patchcam.app.pipeline.KnowledgeTopic
import com.patchcam.app.pipeline.LocalDebugAnswerer
import java.text.DateFormat
import java.util.Date

private val PatchCamBackground = Color(0xFF0B0D12)
private val PatchCamCard = Color(0xFF171A22)
private val PatchCamCard2 = Color(0xFF20242E)
private val PatchCamPrimary = Color(0xFFAFC5FF)
private val PatchCamMuted = Color(0xFF929AAF)
private val PatchCamGood = Color(0xFF77E0A7)
private val PatchCamBad = Color(0xFFFF8798)

@Composable
fun PatchCamApp(
    screen: ScreenStep,
    frames: List<List<OcrLineItem>>,
    liveLines: List<OcrLineItem>,
    diagnosis: Diagnosis?,
    history: List<HistoryItem>,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    onCaptureFrame: (List<OcrLineItem>) -> Unit,
    onLiveLines: (List<OcrLineItem>) -> Unit,
    onGalleryFrame: (List<OcrLineItem>) -> Unit,
    onAnalyze: () -> Unit,
    onNewScan: () -> Unit,
    onHistory: () -> Unit,
    onKnowledge: () -> Unit,
    onFlow: () -> Unit,
    onScan: () -> Unit,
    onSend: () -> Unit,
    onChatSend: (String) -> Unit,
    chatLoading: Boolean,
    sendMessage: String?,
    onDismissMessage: () -> Unit,
    feedbackVote: FeedbackVote?,
    ruleStats: FeedbackStats,
    totalStats: FeedbackStats,
    onFeedback: (FeedbackVote) -> Unit,
    onResetFeedback: () -> Unit,
    onReanalyze: (String) -> Unit,
    onOpenResult: () -> Unit,
    laptopHost: String,
    onLaptopHostChange: (String) -> Unit,
    onOfficeKit: () -> Unit,
    onOfficeKitClipboard: () -> Unit,
    onOfficeKitMirror: () -> Unit,
    officeKitStats: OfficeKitStats,
    onVoiceAsk: () -> Unit,
    modelReady: Boolean,
    modelStatus: String?,
    onLoadModel: () -> Unit
) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = PatchCamBackground,
            surface = PatchCamCard,
            primary = PatchCamPrimary,
            onPrimary = Color(0xFF11141B),
            onBackground = Color.White,
            onSurface = Color.White
        )
    ) {
        Scaffold(
            containerColor = PatchCamBackground,
            topBar = {
                PatchCamHeader(
                    screen = screen,
                    onNewScan = onNewScan
                )
            },
            bottomBar = {
                PatchCamBottomBar(
                    screen = screen,
                    onScan = onScan,
                    onHistory = onHistory,
                    onKnowledge = onKnowledge,
                    onFlow = onFlow
                )
            }
        ) { paddingValues ->

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {

                when (screen) {

                    ScreenStep.SCAN -> {
                        ScanHome(
                            frames = frames,
                            liveLines = liveLines,
                            onCaptureFrame = onCaptureFrame,
                            onLiveLines = onLiveLines,
                            onGalleryFrame = onGalleryFrame,
                            onAnalyze = onAnalyze
                        )
                    }

                    ScreenStep.ANALYZING -> {
                        AnalyzingScreen()
                    }

                    ScreenStep.RESULT -> {
                        ResultScreen(
                            diagnosis = diagnosis,
                            pairingCode = pairingCode,
                            onPairingCodeChange = onPairingCodeChange,
                            onSend = onSend,
                            onChatSend = onChatSend,
                            chatLoading = chatLoading,
                            onNewScan = onNewScan,
                            laptopHost = laptopHost,
                            onLaptopHostChange = onLaptopHostChange,
                            onOfficeKit = onOfficeKit,
                            onOfficeKitClipboard = onOfficeKitClipboard,
                            onOfficeKitMirror = onOfficeKitMirror,
                            officeKitStats = officeKitStats,
                            onVoiceAsk = onVoiceAsk,
                            modelReady = modelReady,
                            modelStatus = modelStatus,
                            onLoadModel = onLoadModel
                        )
                    }

                    ScreenStep.HISTORY -> {
                        HistoryScreen(
                            history = history,
                            onScan = onScan
                        )
                    }

                    ScreenStep.KNOWLEDGE -> {
                        KnowledgeScreen(
                            diagnosis = diagnosis,
                            onOpenResult = onOpenResult
                        )
                    }

                    ScreenStep.FLOW -> {
                        FlowScreen(
                            diagnosis = diagnosis,
                            feedbackVote = feedbackVote,
                            ruleStats = ruleStats,
                            totalStats = totalStats,
                            onFeedback = onFeedback,
                            onResetFeedback = onResetFeedback,
                            onReanalyze = onReanalyze,
                            onOpenResult = onOpenResult,
                            onScan = onScan
                        )
                    }
                }
            }
        }

        if (sendMessage != null) {
            AlertDialog(
                onDismissRequest = onDismissMessage,
                title = {
                    Text("Laptop bridge")
                },
                text = {
                    Text(sendMessage)
                },
                confirmButton = {
                    TextButton(
                        onClick = onDismissMessage
                    ) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* HEADER                                                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun PatchCamHeader(
    screen: ScreenStep,
    onNewScan: () -> Unit
) {
    Surface(
        color = PatchCamBackground
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(70.dp)
                .padding(horizontal = 16.dp)
        ) {

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "PatchCam",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Observe • Diagnose • Patch",
                    color = PatchCamMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            if (
                screen != ScreenStep.SCAN &&
                screen != ScreenStep.ANALYZING
            ) {
                TextButton(
                    onClick = onNewScan,
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Text(
                        text = "NEW SCAN",
                        color = PatchCamPrimary
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* BOTTOM NAVIGATION                                                          */
/* -------------------------------------------------------------------------- */

@Composable
private fun PatchCamBottomBar(
    screen: ScreenStep,
    onScan: () -> Unit,
    onHistory: () -> Unit,
    onKnowledge: () -> Unit,
    onFlow: () -> Unit
) {
    Surface(
        color = Color(0xFF14161D)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {

            SimpleNavButton(
                icon = Icons.Default.CameraAlt,
                label = "Patch",
                selected = screen == ScreenStep.SCAN ||
                        screen == ScreenStep.ANALYZING ||
                        screen == ScreenStep.RESULT,
                onClick = onScan
            )

            SimpleNavButton(
                icon = Icons.Default.History,
                label = "History",
                selected = screen == ScreenStep.HISTORY,
                onClick = onHistory
            )

            SimpleNavButton(
                icon = Icons.Default.AutoStories,
                label = "Knowledge",
                selected = screen == ScreenStep.KNOWLEDGE,
                onClick = onKnowledge
            )

            SimpleNavButton(
                icon = Icons.Default.Timeline,
                label = "Flow",
                selected = screen == ScreenStep.FLOW,
                onClick = onFlow
            )
        }
    }
}

@Composable
private fun SimpleNavButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(80.dp)
            .height(64.dp)
            .background(
                color = if (selected) {
                    Color(0xFF252A36)
                } else {
                    Color.Transparent
                },
                shape = RoundedCornerShape(16.dp)
            )
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        IconButton(
            onClick = onClick,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) {
                    PatchCamPrimary
                } else {
                    PatchCamMuted
                }
            )
        }

        Text(
            text = label,
            color = if (selected) {
                PatchCamPrimary
            } else {
                PatchCamMuted
            },
            fontSize = 11.sp,
            fontWeight = if (selected) {
                FontWeight.Bold
            } else {
                FontWeight.Normal
            }
        )
    }
}

/* -------------------------------------------------------------------------- */
/* SCAN                                                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun ScanHome(
    frames: List<List<OcrLineItem>>,
    liveLines: List<OcrLineItem>,
    onCaptureFrame: (List<OcrLineItem>) -> Unit,
    onLiveLines: (List<OcrLineItem>) -> Unit,
    onGalleryFrame: (List<OcrLineItem>) -> Unit,
    onAnalyze: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        CameraScanner(
            frameCount = frames.size,
            liveLines = liveLines,
            onLiveLines = onLiveLines,
            onCaptureFrame = onCaptureFrame,
            onGalleryFrame = onGalleryFrame,
            onAnalyze = onAnalyze
        )
    }
}

/* -------------------------------------------------------------------------- */
/* ANALYZING                                                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun AnalyzingScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        CircularProgressIndicator(
            color = PatchCamPrimary,
            strokeWidth = 4.dp
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Analyzing your error…",
            color = Color.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Reading code → reconstructing lines → finding the cause → preparing a fix",
            color = PatchCamMuted,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/* -------------------------------------------------------------------------- */
/* RESULT                                                                      */
/* -------------------------------------------------------------------------- */

@Composable
private fun ResultScreen(
    diagnosis: Diagnosis?,
    pairingCode: String,
    onPairingCodeChange: (String) -> Unit,
    onSend: () -> Unit,
    onChatSend: (String) -> Unit,
    chatLoading: Boolean,
    onNewScan: () -> Unit,
    laptopHost: String,
    onLaptopHostChange: (String) -> Unit,
    onOfficeKit: () -> Unit,
    onOfficeKitClipboard: () -> Unit,
    onOfficeKitMirror: () -> Unit,
    officeKitStats: OfficeKitStats,
    onVoiceAsk: () -> Unit,
    modelReady: Boolean,
    modelStatus: String?,
    onLoadModel: () -> Unit
) {
    if (diagnosis == null) {
        EmptyDiagnosisScreen(onNewScan)
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {

        item {
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {

                MetricCard(
                    value = "${diagnosis.confidence}%",
                    label = "confidence",
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    value = "${diagnosis.frameCount}",
                    label = "frames",
                    modifier = Modifier.weight(1f)
                )

                MetricCard(
                    value = "${diagnosis.lineCount}",
                    label = "lines",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (diagnosis.ocrReliability < com.patchcam.app.models.PatchCamThresholds.OCR_RELIABILITY_FLOOR) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF3A2E12),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "SCAN QUALITY TOO LOW TO TRUST",
                            color = Color(0xFFFFD27D),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelSmall
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "I detected a possible programming error, but part of the code could not be read reliably. I don't want to give you a false correction.",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        item {
            SectionCard("WHAT WENT WRONG") {
                ErrorSummary(diagnosis)
            }
        }

        item {
            SectionCard("WHY IT HAPPENED") {

                RichText(
                    text = diagnosis.rootCause,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        item {
            SectionCard("HOW TO FIX IT") {

                val explanation = diagnosis.explanation

                RichText(
                    text = explanation.howToFix,
                    style = MaterialTheme.typography.bodyLarge
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (explanation.correctedExample.isNotBlank()) {
                    val exampleLocation = diagnosis.location
                    val specific =
                        exampleLocation != null &&
                                diagnosis.errorKind != ErrorKind.OTHER &&
                                diagnosis.errorKind != ErrorKind.UNRESOLVED_REFERENCE &&
                                diagnosis.ocrReliability >= PatchCamThresholds.OCR_RELIABILITY_FLOOR

                    Text(
                        text = if (specific && exampleLocation != null) {
                            "Line ${exampleLocation.line} after the fix"
                        } else {
                            "Example"
                        },
                        color = PatchCamPrimary,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        color = Color(0xFF10131A),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text(
                            text = explanation.correctedExample,
                            modifier = Modifier.padding(14.dp),
                            color = PatchCamGood,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        item {
            SectionCard("PROPOSED PATCH") {

                val patch = diagnosis.patch

                if (patch == null) {

                    Text(
                        text = "No safe automatic patch was generated.",
                        color = PatchCamBad
                    )

                } else {

                    Text(
                        text = if (patch.verified) {
                            "VERIFIED PATCH"
                        } else {
                            "REVIEW BEFORE APPLYING"
                        },
                        color = if (patch.verified) {
                            PatchCamGood
                        } else {
                            Color(0xFFFFD27D)
                        },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (patch.oldCode.isNotBlank()) {
                        Text(
                            text = "Line ${patch.targetLine}",
                            color = PatchCamMuted,
                            style = MaterialTheme.typography.labelSmall
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "- ${patch.oldCode}",
                            color = PatchCamBad,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "+ ${patch.newCode}",
                        color = PatchCamGood,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = patch.description,
                        color = PatchCamMuted
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Method: ${patch.matchMethod}",
                        color = PatchCamMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }

        item {
            SectionCard("SCAN DETAILS") {

                Text(
                    text = "PatchCam merged ${diagnosis.frameCount} frame(s) and detected ${diagnosis.lineCount} readable line(s).",
                    color = PatchCamMuted
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "For long errors, scan the screen section by section. PatchCam removes overlapping lines before diagnosis.",
                    color = PatchCamMuted
                )
            }
        }


        item {
            PatchCamChat(
                messages = diagnosis.chat,
                loading = chatLoading,
                onSend = onChatSend,
                onVoiceAsk = onVoiceAsk,
                modelReady = modelReady,
                modelStatus = modelStatus,
                onLoadModel = onLoadModel
            )
        }

        item {
            SectionCard("SEND THE FIX TO YOUR LAPTOP") {

                var showAddress by remember { mutableStateOf(false) }

                Text(
                    text = "On the laptop run  python patchcam_bridge.py  and type the 6-digit code it shows on its screen.",
                    color = PatchCamMuted,
                    style = MaterialTheme.typography.bodySmall
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = pairingCode,
                    onValueChange = onPairingCodeChange,
                    label = {
                        Text("Pairing code (shown on the laptop)")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onSend,
                    enabled = diagnosis.patch != null &&
                            pairingCode.length == 6,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {

                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send patch"
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Send patch to laptop (Wi-Fi)",
                        fontWeight = FontWeight.Bold
                    )
                }

                TextButton(onClick = { showAddress = !showAddress }) {
                    Text(
                        text = if (showAddress) {
                            "Hide laptop address"
                        } else {
                            "Laptop not found? Enter its address"
                        },
                        fontSize = 12.sp
                    )
                }

                if (showAddress) {
                    OutlinedTextField(
                        value = laptopHost,
                        onValueChange = onLaptopHostChange,
                        label = {
                            Text("Laptop address, e.g. 192.168.1.20:8765")
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        item {
            OfficeKitPanel(
                hasPatch = diagnosis.patch != null,
                stats = officeKitStats,
                onFileShare = onOfficeKit,
                onClipboardSync = onOfficeKitClipboard,
                onMirror = onOfficeKitMirror
            )
        }

        item {

            OutlinedButton(
                onClick = onNewScan,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Scan another problem")
            }
        }
    }
}


/* -------------------------------------------------------------------------- */
/* OFFICE KIT PANEL                                                            */
/* -------------------------------------------------------------------------- */

/**
 * PatchCam's three real Office Kit touchpoints, side by side, plus an
 * honest on-device count of how many times each has actually been used
 * this session (via OfficeKitSessionTracker). There's no public Office
 * Kit SDK, so each button drives the same system surface a person would
 * use by hand — see OfficeKitShare for what each one actually does.
 */
@Composable
private fun OfficeKitPanel(
    hasPatch: Boolean,
    stats: OfficeKitStats,
    onFileShare: () -> Unit,
    onClipboardSync: () -> Unit,
    onMirror: () -> Unit
) {
    SectionCard("iQOO OFFICE KIT") {

        Text(
            text = "Three real Office Kit surfaces — pick whichever fits the moment.",
            color = PatchCamMuted,
            style = MaterialTheme.typography.bodySmall
        )

        Spacer(modifier = Modifier.height(10.dp))

        OfficeKitActionRow(
            title = "Copy patch (clipboard sync)",
            subtitle = "Fastest — paste straight into the laptop editor.",
            count = stats.clipboardSyncs,
            enabled = hasPatch,
            onClick = onClipboardSync
        )

        Spacer(modifier = Modifier.height(8.dp))

        OfficeKitActionRow(
            title = "Send file (EasyShare)",
            subtitle = "Full structured patch + backup, works with no shared Wi-Fi.",
            count = stats.fileShares,
            enabled = hasPatch,
            onClick = onFileShare
        )

        Spacer(modifier = Modifier.height(8.dp))

        OfficeKitActionRow(
            title = "Mirroring for this demo",
            subtitle = "Tap once screen mirroring is on, for the live pitch.",
            count = stats.mirrorSessions,
            enabled = true,
            onClick = onMirror
        )

        if (stats.total > 0) {
            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Office Kit used ${stats.total} time(s) this session " +
                        "(${stats.clipboardSyncs} clipboard · ${stats.fileShares} file · " +
                        "${stats.mirrorSessions} mirror).",
                color = PatchCamPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun OfficeKitActionRow(
    title: String,
    subtitle: String,
    count: Int,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start
        ) {
            Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(text = subtitle, color = PatchCamMuted, fontSize = 10.sp)
        }

        if (count > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "×$count",
                color = PatchCamPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* CONTEXTUAL DEBUG CHAT                                                       */
/* -------------------------------------------------------------------------- */

@Composable
private fun PatchCamChat(
    messages: List<ChatMessage>,
    loading: Boolean,
    onSend: (String) -> Unit,
    onVoiceAsk: () -> Unit,
    modelReady: Boolean,
    modelStatus: String?,
    onLoadModel: () -> Unit
) {
    var text by remember { mutableStateOf("") }

    SectionCard("ASK PATCHCAM") {

        ModelStatusRow(
            ready = modelReady,
            status = modelStatus,
            onLoad = onLoadModel
        )

        if (messages.isEmpty()) {
            Text(
                text = "Ask a question about this diagnosis, the source code, or the proposed patch.",
                color = PatchCamMuted
            )
        } else {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                messages.takeLast(6).forEach { message ->
                    ChatBubble(message)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Ask about this error...")
                },
                maxLines = 4,
                enabled = !loading
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = onVoiceAsk,
                enabled = !loading
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Ask PatchCam by voice",
                    tint = if (!loading) PatchCamPrimary else PatchCamMuted
                )
            }

            IconButton(
                onClick = {
                    val question = text.trim()
                    if (question.isNotBlank()) {
                        onSend(question)
                        text = ""
                    }
                },
                enabled = text.isNotBlank() && !loading
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Ask PatchCam",
                    tint = if (text.isNotBlank() && !loading) {
                        PatchCamPrimary
                    } else {
                        PatchCamMuted
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LocalDebugAnswerer.suggestedQuestions.forEach { question ->
                ChatSuggestion(
                    text = question,
                    enabled = !loading,
                    onClick = { onSend(question) }
                )
            }
        }

        if (loading) {
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "PatchCam is thinking...",
                    color = PatchCamMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun ChatSuggestion(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(
            horizontal = 10.dp,
            vertical = 4.dp
        )
    ) {
        Text(
            text = text,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage
) {
    val user = message.role == ChatRole.USER

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (user) Color(0xFF202B3D) else Color(0xFF10131A),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(11.dp)
        ) {
            Text(
                text = if (user) "YOU" else "PATCHCAM",
                color = if (user) PatchCamPrimary else PatchCamGood,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            if (user) {
                Text(
                    text = message.text,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                RichText(text = message.text)
            }
        }
    }
}

@Composable
private fun ModelStatusRow(
    ready: Boolean,
    status: String?,
    onLoad: () -> Unit
) {
    val canLoad =
        (!ready && status == null) ||
                (status != null && status.startsWith("Model import failed"))

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = when {
                status != null -> status
                ready -> "On-device AI (Gemma) is ready"
                else -> "On-device AI not installed. Answers use PatchCam's built-in engine."
            },
            modifier = Modifier.weight(1f),
            color = if (ready && status == null) PatchCamGood else PatchCamMuted,
            style = MaterialTheme.typography.labelSmall
        )

        if (canLoad) {
            TextButton(onClick = onLoad) {
                Text(text = "Load model", fontSize = 12.sp)
            }
        }
    }

    Spacer(modifier = Modifier.height(6.dp))
}

/* -------------------------------------------------------------------------- */
/* EMPTY DIAGNOSIS                                                             */
/* -------------------------------------------------------------------------- */

@Composable
private fun EmptyDiagnosisScreen(
    onNewScan: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "No diagnosis available",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Start a new scan to analyze a coding error.",
            color = PatchCamMuted
        )

        Spacer(modifier = Modifier.height(18.dp))

        Button(
            onClick = onNewScan
        ) {
            Text("Start scan")
        }
    }
}

/* -------------------------------------------------------------------------- */
/* METRIC CARD                                                                 */
/* -------------------------------------------------------------------------- */

@Composable
private fun MetricCard(
    value: String,
    label: String,
    modifier: Modifier
) {
    Surface(
        modifier = modifier,
        color = PatchCamCard2,
        shape = RoundedCornerShape(18.dp)
    ) {

        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Text(
                text = value,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = label,
                color = PatchCamMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* SECTION CARD                                                                */
/* -------------------------------------------------------------------------- */

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PatchCamCard,
        shape = RoundedCornerShape(22.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(17.dp)
        ) {

            Text(
                text = title,
                color = PatchCamMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            content()
        }
    }
}

/* -------------------------------------------------------------------------- */
/* HISTORY                                                                     */
/* -------------------------------------------------------------------------- */

@Composable
private fun HistoryScreen(
    history: List<HistoryItem>,
    onScan: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {

        item {

            Text(
                text = "Patch history",
                color = Color.White,
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Diagnoses created during this session.",
                color = PatchCamMuted
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        if (history.isEmpty()) {

            item {

                SectionCard("EMPTY") {

                    Text(
                        text = "No patches yet. Scan a real error and PatchCam will record the diagnosis here.",
                        color = PatchCamMuted
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = onScan
                    ) {
                        Text("Start first scan")
                    }
                }
            }

        } else {

            items(
                items = history,
                key = { it.id }
            ) { historyItem ->

                SectionCard(historyItem.title) {

                    Text(
                        text = historyItem.category,
                        color = PatchCamMuted
                    )

                    Spacer(modifier = Modifier.height(5.dp))

                    Text(
                        text = "${historyItem.confidence}% confidence • ${
                            DateFormat.getDateTimeInstance()
                                .format(Date(historyItem.timestamp))
                        }",
                        color = Color.White
                    )
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* SHARED TEXT HELPERS                                                         */
/* -------------------------------------------------------------------------- */

/*
 * Text that understands two bits of markup used by PatchCam's answers:
 *  - `inline code`        -> monospace, green
 *  - ``` fenced blocks ``` -> a code box
 */
@Composable
private fun RichText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    style: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val parts = text.split("```")

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        parts.forEachIndexed { index, part ->
            val chunk = part.trim('\n')

            if (chunk.isNotEmpty()) {
                if (index % 2 == 1) {
                    Surface(
                        color = Color(0xFF10131A),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = chunk,
                            modifier = Modifier
                                .padding(10.dp)
                                .horizontalScroll(rememberScrollState()),
                            color = PatchCamGood,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    Text(
                        text = inlineCode(chunk),
                        color = color,
                        style = style
                    )
                }
            }
        }
    }
}

private fun inlineCode(text: String): AnnotatedString =
    buildAnnotatedString {
        text.split("`").forEachIndexed { index, segment ->
            if (index % 2 == 1) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = PatchCamGood
                    )
                ) {
                    append(segment)
                }
            } else {
                append(segment)
            }
        }
    }

@Composable
private fun CodeBox(
    label: String,
    code: String,
    color: Color
) {
    Column {
        Text(
            text = label,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF10131A),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = code,
                modifier = Modifier
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState()),
                color = color,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun FilterPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = if (selected) PatchCamPrimary else PatchCamCard2,
        shape = RoundedCornerShape(50.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            color = if (selected) Color(0xFF11141B) else Color.White,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/* -------------------------------------------------------------------------- */
/* KNOWLEDGE                                                                   */
/* -------------------------------------------------------------------------- */

@Composable
private fun KnowledgeScreen(
    diagnosis: Diagnosis?,
    onOpenResult: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf<String?>(null) }
    var expandedId by rememberSaveable { mutableStateOf<String?>(null) }

    // topic id -> the line number (0-based) the user last tapped
    val picks = remember { mutableStateMapOf<String, Int>() }

    val related = diagnosis?.let { KnowledgeBase.topicForKind(it.errorKind) }

    val pinRelated = related != null && query.isBlank() && category == null

    val results = KnowledgeBase
        .search(query, category)
        .filter { topic -> !(pinRelated && topic.id == related?.id) }

    val challengeTotal = KnowledgeBase.topics.count { it.hasChallenge }

    val spotted = KnowledgeBase.topics.count {
        it.hasChallenge && picks[it.id] == it.bugIndex
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {

        item {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Knowledge",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Search a topic, open it, then test yourself: tap the line that has the bug.",
                color = PatchCamMuted
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                color = PatchCamCard2,
                shape = RoundedCornerShape(50.dp)
            ) {
                Text(
                    text = "Bugs spotted: $spotted / $challengeTotal",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = if (spotted > 0) PatchCamGood else PatchCamMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = {
                    Text("Search: colon, indent, NameError…")
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search"
                    )
                }
            )
        }

        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterPill(
                    text = "All",
                    selected = category == null,
                    onClick = { category = null }
                )

                KnowledgeBase.categories.forEach { name ->
                    FilterPill(
                        text = name,
                        selected = category == name,
                        onClick = {
                            category = if (category == name) null else name
                        }
                    )
                }
            }
        }

        if (pinRelated && related != null) {
            item(key = "related-to-last-scan") {
                Column {
                    Text(
                        text = "RELATED TO YOUR LAST SCAN",
                        color = PatchCamGood,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    KnowledgeTopicCard(
                        topic = related,
                        expanded = expandedId == related.id,
                        picked = picks[related.id],
                        onToggle = {
                            expandedId = if (expandedId == related.id) null else related.id
                        },
                        onPick = { picks[related.id] = it },
                        onOpenResult = onOpenResult
                    )
                }
            }
        }

        if (results.isEmpty()) {
            item {
                SectionCard("NO MATCH") {
                    Text(
                        text = "Nothing matches \"$query\". Try a shorter word, or clear the filters.",
                        color = PatchCamMuted
                    )

                    TextButton(
                        onClick = {
                            query = ""
                            category = null
                        }
                    ) {
                        Text("Clear search")
                    }
                }
            }
        } else {
            items(items = results, key = { it.id }) { topic ->
                KnowledgeTopicCard(
                    topic = topic,
                    expanded = expandedId == topic.id,
                    picked = picks[topic.id],
                    onToggle = {
                        expandedId = if (expandedId == topic.id) null else topic.id
                    },
                    onPick = { picks[topic.id] = it },
                    onOpenResult = null
                )
            }
        }
    }
}

@Composable
private fun KnowledgeTopicCard(
    topic: KnowledgeTopic,
    expanded: Boolean,
    picked: Int?,
    onToggle: () -> Unit,
    onPick: (Int) -> Unit,
    onOpenResult: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = PatchCamCard,
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.padding(17.dp)) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = topic.category.uppercase(),
                        color = PatchCamPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = topic.title,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = topic.summary,
                        color = PatchCamMuted
                    )
                }

                Icon(
                    imageVector = if (expanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = PatchCamMuted
                )
            }

            if (expanded) {

                if (topic.meaning.isNotBlank() && topic.meaning != topic.summary) {
                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "WHAT IT MEANS",
                        color = PatchCamMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(text = topic.meaning, color = Color.White)
                }

                if (topic.cause.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "COMMON CAUSE",
                        color = PatchCamMuted,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(text = topic.cause, color = Color.White)
                }

                if (topic.broken.isNotBlank() && topic.fixed.isNotBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))

                    CodeBox(label = "BROKEN", code = topic.broken, color = PatchCamBad)

                    Spacer(modifier = Modifier.height(8.dp))

                    CodeBox(label = "FIXED", code = topic.fixed, color = PatchCamGood)
                }

                if (topic.hasChallenge) {
                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "TRY IT: TAP THE LINE WITH THE BUG",
                        color = PatchCamPrimary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    topic.challengeLines.forEachIndexed { index, line ->

                        val isPicked = picked == index
                        val isBug = index == topic.bugIndex

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clickable { onPick(index) },
                            color = when {
                                isPicked && isBug -> Color(0xFF14352A)
                                isPicked -> Color(0xFF3A1F27)
                                else -> Color(0xFF10131A)
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 10.dp
                                )
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    color = PatchCamMuted,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )

                                Spacer(modifier = Modifier.width(12.dp))

                                Text(
                                    text = line,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    if (picked != null) {
                        Spacer(modifier = Modifier.height(8.dp))

                        if (picked == topic.bugIndex) {
                            Text(
                                text = "✓ Correct! ${topic.bugWhy}",
                                color = PatchCamGood,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "✕ Not that line. Try another one.",
                                color = PatchCamBad,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (topic.tip.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Tip: ${topic.tip}",
                        color = PatchCamMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (onOpenResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(onClick = onOpenResult) {
                        Text("Back to my diagnosis")
                    }
                }
            }
        }
    }
}

/* -------------------------------------------------------------------------- */
/* FLOW                                                                        */
/* -------------------------------------------------------------------------- */

@Composable
private fun FlowScreen(
    diagnosis: Diagnosis?,
    feedbackVote: FeedbackVote?,
    ruleStats: FeedbackStats,
    totalStats: FeedbackStats,
    onFeedback: (FeedbackVote) -> Unit,
    onResetFeedback: () -> Unit,
    onReanalyze: (String) -> Unit,
    onOpenResult: () -> Unit,
    onScan: () -> Unit
) {
    val steps = listOf(
        "Observe",
        "Reconstruct",
        "Diagnose",
        "Validate",
        "Update"
    )

    var expanded by remember { mutableStateOf(if (diagnosis != null) 4 else -1) }
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {

        item {
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Feedback loop",
                color = Color.White,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Tap a step to see what PatchCam did. Correct what it misread, and tell it how the fix went: it learns from you.",
                color = PatchCamMuted
            )
        }

        items(items = steps.withIndex().toList(), key = { it.index }) { entry ->

            val index = entry.index
            val open = expanded == index
            val done = diagnosis != null && index < 4

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = PatchCamCard,
                shape = RoundedCornerShape(18.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = if (open) -1 else index },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = if (done) PatchCamGood else PatchCamCard2,
                            shape = RoundedCornerShape(50.dp)
                        ) {
                            Text(
                                text = "${index + 1}",
                                modifier = Modifier
                                    .padding(horizontal = 11.dp, vertical = 5.dp),
                                color = if (done) Color(0xFF11141B) else Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = entry.value,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )

                            if (!open) {
                                Text(
                                    text = flowDescription(index, diagnosis),
                                    color = PatchCamMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Icon(
                            imageVector = if (open) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = if (open) "Collapse" else "Expand",
                            tint = PatchCamMuted
                        )
                    }

                    if (open) {
                        Spacer(modifier = Modifier.height(12.dp))

                        when (index) {
                            0 -> FlowObserveDetail(diagnosis, onScan)

                            1 -> FlowReconstructDetail(
                                diagnosis = diagnosis,
                                editing = editing,
                                draft = draft,
                                onEditing = { editing = it },
                                onDraft = { draft = it },
                                onReanalyze = onReanalyze
                            )

                            2 -> FlowDiagnoseDetail(diagnosis, onOpenResult)

                            3 -> FlowValidateDetail(diagnosis)

                            else -> FlowUpdateDetail(
                                diagnosis = diagnosis,
                                feedbackVote = feedbackVote,
                                ruleStats = ruleStats,
                                totalStats = totalStats,
                                onFeedback = onFeedback,
                                onResetFeedback = onResetFeedback
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlowObserveDetail(
    diagnosis: Diagnosis?,
    onScan: () -> Unit
) {
    if (diagnosis == null) {
        Text(
            text = "Point the camera at an error and PatchCam reads it with on-device OCR.",
            color = PatchCamMuted
        )

        Spacer(modifier = Modifier.height(10.dp))

        OutlinedButton(onClick = onScan) {
            Text("Open the camera")
        }

        return
    }

    Text(
        text = "PatchCam read ${diagnosis.lineCount} line(s) from ${diagnosis.frameCount} frame(s).",
        color = Color.White
    )

    Spacer(modifier = Modifier.height(10.dp))

    Text(
        text = "SCAN CLARITY  ${diagnosis.ocrReliability}%",
        color = PatchCamMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(6.dp))

    val trusted = diagnosis.ocrReliability >= PatchCamThresholds.OCR_RELIABILITY_FLOOR

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Color(0xFF303544), RoundedCornerShape(50.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(diagnosis.ocrReliability.coerceIn(0, 100) / 100f)
                .height(8.dp)
                .background(
                    if (trusted) PatchCamGood else Color(0xFFFFD27D),
                    RoundedCornerShape(50.dp)
                )
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = if (trusted) {
            "Clear enough to trust."
        } else {
            "Too blurry to trust. Scan again, or correct the code in step 2."
        },
        color = PatchCamMuted
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedButton(onClick = onScan) {
        Text("Capture more of the screen")
    }
}

@Composable
private fun FlowReconstructDetail(
    diagnosis: Diagnosis?,
    editing: Boolean,
    draft: String,
    onEditing: (Boolean) -> Unit,
    onDraft: (String) -> Unit,
    onReanalyze: (String) -> Unit
) {
    if (diagnosis == null) {
        Text(
            text = "The scanned lines are rebuilt here with their indentation.",
            color = PatchCamMuted
        )
        return
    }

    if (editing) {

        Text(
            text = "Fix any line PatchCam read wrongly, then re-analyze.",
            color = PatchCamMuted
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = draft,
            onValueChange = onDraft,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            ),
            maxLines = 12
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    onReanalyze(draft)
                    onEditing(false)
                },
                enabled = draft.isNotBlank()
            ) {
                Text("Re-analyze")
            }

            TextButton(onClick = { onEditing(false) }) {
                Text("Cancel")
            }
        }

        return
    }

    Text(
        text = "Lines as PatchCam read them:",
        color = PatchCamMuted
    )

    Spacer(modifier = Modifier.height(6.dp))

    val failing = diagnosis.location?.codeIndex

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFF10131A),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .heightIn(max = 240.dp)
                .verticalScroll(rememberScrollState())
        ) {
            diagnosis.code.lines().forEachIndexed { i, line ->

                val isFailing = failing != null && i + 1 == failing

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isFailing) Color(0xFF3A1F27) else Color.Transparent
                        )
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isFailing) "▸" else " ",
                        color = PatchCamBad,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = line.ifEmpty { " " },
                        modifier = Modifier.weight(1f),
                        color = if (isFailing) PatchCamBad else Color.White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp
                    )

                    if (isFailing) {
                        val shown = diagnosis.location?.line

                        if (shown != null) {
                            Text(
                                text = "line $shown",
                                color = PatchCamMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }

    if (diagnosis.userEdited) {
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "This analysis uses the code you corrected.",
            color = PatchCamGood,
            style = MaterialTheme.typography.labelSmall
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedButton(
        onClick = {
            onDraft(diagnosis.code)
            onEditing(true)
        }
    ) {
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text("Something misread? Edit")
    }
}

@Composable
private fun FlowDiagnoseDetail(
    diagnosis: Diagnosis?,
    onOpenResult: () -> Unit
) {
    if (diagnosis == null) {
        Text(
            text = "The cause appears here after a scan.",
            color = PatchCamMuted
        )
        return
    }

    RichText(text = diagnosis.rootCause)

    Spacer(modifier = Modifier.height(10.dp))

    val patch = diagnosis.patch

    Text(
        text = if (patch != null && patch.newCode.isNotBlank()) {
            "Fix found by: " + when (patch.matchMethod) {
                MatchMethod.LOCAL_TABLE -> "PatchCam's built-in fix rules"
                MatchMethod.ON_DEVICE_LLM -> "the on-device language model"
                MatchMethod.HEURISTIC -> "a safe heuristic"
            }
        } else {
            "No automatic patch was produced."
        },
        color = PatchCamMuted
    )

    Text(
        text = "Confidence ${diagnosis.confidence}% is an evidence score, not a guarantee.",
        color = PatchCamMuted
    )

    Spacer(modifier = Modifier.height(10.dp))

    OutlinedButton(onClick = onOpenResult) {
        Text("See the fix and ask PatchCam")
    }
}

@Composable
private fun StatusLine(
    title: String,
    text: String,
    ok: Boolean?
) {
    Row(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = when (ok) {
                true -> "✓"
                false -> "✕"
                null -> "–"
            },
            color = when (ok) {
                true -> PatchCamGood
                false -> PatchCamBad
                null -> PatchCamMuted
            },
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(10.dp))

        Column {
            Text(
                text = title,
                color = PatchCamMuted,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )

            Text(text = text, color = Color.White)
        }
    }
}

@Composable
private fun FlowValidateDetail(diagnosis: Diagnosis?) {
    if (diagnosis == null) {
        Text(
            text = "PatchCam checks every Python fix with the real Python parser on your phone.",
            color = PatchCamMuted
        )
        return
    }

    val before = diagnosis.sourceValidation
    val after = diagnosis.validation

    StatusLine(
        title = "AS SCANNED",
        text = when {
            before == null ->
                "Not checked: the on-device parser only runs on Python code."

            before.valid ->
                "Python's parser accepts the code as scanned."

            else ->
                "Python's parser stops: " +
                        (before.errorMessage ?: "syntax error").substringBefore(" (") +
                        (diagnosis.location?.let { " (${it.label().lowercase()})" } ?: "")
        },
        ok = before?.valid
    )

    StatusLine(
        title = "AFTER THE PATCH",
        text = when {
            after == null ->
                "No patch was checked."

            after.valid ->
                "Python's parser accepts the patched code."

            else ->
                "Still fails: " + (after.errorMessage ?: "syntax error").substringBefore(" (")
        },
        ok = after?.valid
    )
}

@Composable
private fun VoteButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
        ) {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
        ) {
            Text(text = label, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun FlowUpdateDetail(
    diagnosis: Diagnosis?,
    feedbackVote: FeedbackVote?,
    ruleStats: FeedbackStats,
    totalStats: FeedbackStats,
    onFeedback: (FeedbackVote) -> Unit,
    onResetFeedback: () -> Unit
) {
    if (diagnosis == null) {
        Text(
            text = "After you try a fix, tell PatchCam how it went. It learns from your answer.",
            color = PatchCamMuted
        )
        return
    }

    Text(
        text = "Did the suggested fix work?",
        color = Color.White,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        VoteButton(
            label = "✓ Worked",
            selected = feedbackVote == FeedbackVote.WORKED,
            modifier = Modifier.weight(1f),
            onClick = { onFeedback(FeedbackVote.WORKED) }
        )

        VoteButton(
            label = "~ Partly",
            selected = feedbackVote == FeedbackVote.PARTLY,
            modifier = Modifier.weight(1f),
            onClick = { onFeedback(FeedbackVote.PARTLY) }
        )

        VoteButton(
            label = "✕ Didn't",
            selected = feedbackVote == FeedbackVote.DIDNT_WORK,
            modifier = Modifier.weight(1f),
            onClick = { onFeedback(FeedbackVote.DIDNT_WORK) }
        )
    }

    Spacer(modifier = Modifier.height(8.dp))

    Text(
        text = when (feedbackVote) {
            FeedbackVote.WORKED ->
                "Great. PatchCam will trust this kind of fix a bit more next time."

            FeedbackVote.PARTLY ->
                "Thanks. PatchCam will keep suggesting it, with only a little extra confidence."

            FeedbackVote.DIDNT_WORK ->
                "Thanks. PatchCam will be more cautious with this kind of fix and lower its confidence."

            null ->
                "Your answer stays on this phone."
        },
        color = PatchCamMuted
    )

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = "WHAT PATCHCAM HAS LEARNED",
        color = PatchCamMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(4.dp))

    Text(
        text = "This kind of fix: ${ruleStats.worked} worked · ${ruleStats.partly} partly · ${ruleStats.failed} didn't work",
        color = Color.White
    )

    if (ruleStats.total > 0) {
        val shift = FixFeedbackStore.confidenceAdjustment(ruleStats)

        Text(
            text = "Confidence shift for similar fixes: " + if (shift >= 0) "+$shift" else "$shift",
            color = PatchCamMuted
        )
    }

    Text(
        text = "PatchCam has learned from ${totalStats.total} of your answers so far.",
        color = PatchCamMuted
    )

    if (totalStats.total > 0) {
        TextButton(onClick = onResetFeedback) {
            Text(
                text = "Reset what PatchCam learned",
                color = PatchCamBad
            )
        }
    }
}

/* -------------------------------------------------------------------------- */
/* COMPACT ERROR                                                               */
/* -------------------------------------------------------------------------- */

/*
 * The error in a few lines: what it is, the exact line and column, the
 * offending line with a caret under the spot, and one plain sentence.
 * The raw traceback is still available, but only behind a toggle.
 */
@Composable
private fun ErrorSummary(diagnosis: Diagnosis) {

    var showTechnical by remember { mutableStateOf(false) }

    val location = diagnosis.location

    Text(
        text = diagnosis.category,
        color = Color.White,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold
    )

    Spacer(modifier = Modifier.height(8.dp))

    Row(verticalAlignment = Alignment.CenterVertically) {

        if (location != null) {
            Surface(
                color = Color(0xFF3A1F27),
                shape = RoundedCornerShape(50.dp)
            ) {
                Text(
                    text = "LINE ${location.line}" +
                            (location.column?.let { " · COL $it" } ?: ""),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = PatchCamBad,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
        }

        Text(
            text = diagnosis.sourceFile
                ?: if (location == null) buildSourceText(diagnosis) else "",
            color = PatchCamMuted,
            fontSize = 12.sp
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    RichText(
        text = diagnosis.explanation.whatWentWrong,
        style = MaterialTheme.typography.bodyLarge
    )

    if (location != null && location.lineText.isNotBlank()) {

        Spacer(modifier = Modifier.height(10.dp))

        val gutter = "${location.line} │ "

        val caret = location.column?.let {
            " ".repeat(gutter.length + (it - 1).coerceAtLeast(0)) + "^"
        }

        val annotated = buildAnnotatedString {
            withStyle(SpanStyle(color = PatchCamMuted)) {
                append(gutter)
            }

            withStyle(SpanStyle(color = Color.White)) {
                append(location.lineText)
            }

            if (caret != null) {
                append("\n")

                withStyle(
                    SpanStyle(
                        color = PatchCamBad,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(caret)
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = Color(0xFF10131A),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = annotated,
                modifier = Modifier
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState()),
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            )
        }
    }

    if (diagnosis.userEdited) {
        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Based on the code you corrected.",
            color = PatchCamGood,
            style = MaterialTheme.typography.labelSmall
        )
    }

    TextButton(
        onClick = { showTechnical = !showTechnical },
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        Text(
            text = if (showTechnical) "Hide technical details" else "Show technical details",
            color = PatchCamPrimary,
            fontSize = 12.sp
        )

        Icon(
            imageVector = if (showTechnical) {
                Icons.Default.KeyboardArrowUp
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = null,
            tint = PatchCamPrimary,
            modifier = Modifier.size(18.dp)
        )
    }

    if (showTechnical) {
        Text(
            text = diagnosis.errorText,
            color = Color(0xFFFFC3CA),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp
        )
    }
}

/* -------------------------------------------------------------------------- */
/* HELPERS                                                                     */
/* -------------------------------------------------------------------------- */

private fun buildSourceText(
    diagnosis: Diagnosis
): String {
    val file = diagnosis.sourceFile
    val line = diagnosis.lineNumber

    return when {
        file != null && line != null -> "$file:$line"
        file != null -> file
        else -> "Source location detected visually"
    }
}

private fun flowDescription(
    index: Int,
    diagnosis: Diagnosis?
): String {
    return when (index) {

        0 -> {
            if (diagnosis != null) {
                "Visual evidence captured."
            } else {
                "Waiting for camera evidence."
            }
        }

        1 -> {
            if (diagnosis != null) {
                "Frames merged and source structure reconstructed."
            } else {
                "Waiting for captured frames."
            }
        }

        2 -> {
            diagnosis?.rootCause
                ?: "The root cause will appear after analysis."
        }

        3 -> {
            val validation = diagnosis?.validation

            when {
                validation == null ->
                    "Validation depends on the detected language."

                validation.valid ->
                    "The generated patch passed the available validation."

                else ->
                    "Validation requires manual review."
            }
        }

        else -> {
            if (diagnosis != null) {
                "Review the explanation, then apply or send the patch."
            } else {
                "Scan an error to begin."
            }
        }
    }
}