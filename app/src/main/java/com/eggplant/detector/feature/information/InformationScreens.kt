package com.eggplant.detector.feature.information

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eggplant.detector.BuildConfig
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.eggplant.detector.R

data class InfoSection(val heading: String, val body: String)

@Composable
fun InformationScreen(
    title: String,
    introduction: String,
    sections: List<InfoSection>,
    onBack: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                }
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    Text(
                        introduction,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        items(sections.size) { index ->
            val section = sections[index]
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(section.heading, fontWeight = FontWeight.SemiBold)
                        Text(section.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun ScanTipsScreen(onBack: () -> Unit) = InformationScreen(
    title = stringResource(R.string.scan_quality_tips),
    introduction = localized("Clear, steady images help the on-device detector produce more useful results.", "Nakakatulong ang malinaw at matatag na larawan upang maging mas kapaki-pakinabang ang resulta ng on-device detector."),
    sections = listOf(
        InfoSection(localized("Clean the lens", "Linisin ang lente"), localized("Wipe the camera lens before scanning to avoid haze and false visual details.", "Punasan ang lente bago mag-scan upang maiwasan ang malabo o maling detalye.")),
        InfoSection(localized("Fill the frame", "Punuin ang frame"), localized("Keep the affected leaf or fruit large and centered without cutting off the damaged area.", "Igitna at palakihin sa frame ang apektadong dahon o bunga nang hindi napuputol ang pinsala.")),
        InfoSection(localized("Use even lighting", "Gumamit ng pantay na liwanag"), localized("Use bright natural light when possible. Avoid flash glare, harsh shadows, and colored lighting.", "Gumamit ng maliwanag na likas na ilaw. Iwasan ang silaw, matinding anino, at makulay na ilaw.")),
        InfoSection(localized("Hold steady", "Panatilihing matatag"), localized("Wait for the image to focus and keep the phone still while capturing.", "Hintaying luminaw ang focus at panatilihing hindi gumagalaw ang telepono.")),
        InfoSection(localized("Use a simple background", "Gumamit ng payak na likuran"), localized("Place one specimen against a plain background so surrounding plants do not distract the detector.", "Ilagay ang isang specimen sa payak na likuran upang hindi makagambala ang ibang halaman.")),
        InfoSection(localized("Capture useful angles", "Kunan sa kapaki-pakinabang na anggulo"), localized("Photograph both sides of a leaf and take another angle when symptoms are unclear.", "Kunan ang magkabilang panig ng dahon at ibang anggulo kung hindi malinaw ang sintomas.")),
        InfoSection(localized("Live preview and capture", "Live preview at kuha"), localized("Hold the shutter for live preview, then tap once to capture and analyze a still image for the Result screen.", "Pindutin nang matagal ang shutter para sa live preview, pagkatapos ay i-tap nang isang beses para kumuha at suriin ang still image para sa screen ng Resulta.")),
    ),
    onBack = onBack,
)

@Composable
fun PrivacyScreen(onBack: () -> Unit) = InformationScreen(
    title = stringResource(R.string.data_privacy),
    introduction = localized("Your detector data stays on this Android phone.", "Nananatili sa Android phone na ito ang datos ng detector."),
    sections = listOf(
        InfoSection(localized("Offline operation", "Paggana offline"), localized("The app has no Internet permission and does not use accounts, cloud synchronization, advertising, or analytics.", "Walang pahintulot sa Internet, account, cloud sync, patalastas, o analytics ang app.")),
        InfoSection(localized("Local history", "Lokal na kasaysayan"), localized("Scan records, language, theme, units, and notification read state are stored in the app's local Room/SQLite database.", "Sa lokal na Room/SQLite database iniimbak ang scan, wika, tema, yunit, at nabasang abiso.")),
        InfoSection(localized("Photos", "Mga larawan"), localized("A saved scan keeps a clean JPEG snapshot in private app storage without EXIF or location metadata. Unsaved scans are discarded.", "Ang naka-save na scan ay nagtatago ng malinis na JPEG sa pribadong storage ng app nang walang EXIF o lokasyon. Itinatapon ang hindi naka-save na scan.")),
        InfoSection(localized("Removal", "Pag-alis"), localized("Uninstalling the app removes its local database. Backup and history export are disabled.", "Inaalis ng pag-uninstall ang lokal na database. Naka-disable ang backup at export.")),
        InfoSection(localized("Camera permission", "Pahintulot sa kamera"), localized("Camera access is used only for still capture and optional hold-to-preview live detection. Gallery selection uses Android's system photo picker.", "Ginagamit lamang ang camera access para sa still capture at opsyonal na hold-to-preview live detection. Android system photo picker ang gamit sa Gallery.")),
    ),
    onBack = onBack,
)

@Composable
fun HelpScreen(onBack: () -> Unit) = InformationScreen(
    title = stringResource(R.string.help_faq),
    introduction = localized("Answers for the current offline detector milestone.", "Mga sagot para sa kasalukuyang yugto ng offline detector."),
    sections = listOf(
        InfoSection(localized("Does the app need Internet?", "Kailangan ba ng Internet?"), localized("No. Live detection, Gallery analysis, history, and settings work offline.", "Hindi. Gumagana offline ang live detection, Gallery analysis, kasaysayan, at mga setting.")),
        InfoSection(localized("Is the trained model included?", "Kasama na ba ang sinanay na modelo?"), localized("Yes. The custom YOLO26m export is packaged with the app and executed locally by NCNN.", "Oo. Kasama sa app ang custom YOLO26m export at lokal itong pinapatakbo ng NCNN.")),
        InfoSection(localized("Is history permanent?", "Nananatili ba ang kasaysayan?"), localized("History remains on this phone after restarts. It is removed when the app is uninstalled or its storage is cleared.", "Nananatili ito matapos i-restart at nawawala kapag in-uninstall o binura ang storage ng app.")),
        InfoSection(localized("Which conditions are included?", "Anong kondisyon ang kasama?"), localized("The library contains six leaf conditions and two fruit conditions for the current thesis scope.", "May anim na kondisyon sa dahon at dalawa sa bunga sa kasalukuyang saklaw.")),
        InfoSection(localized("Can this replace an expert?", "Mapapalitan ba nito ang eksperto?"), localized("No. Use results as decision support and consult a qualified agricultural specialist when crop health is uncertain.", "Hindi. Gamitin bilang gabay at kumonsulta sa kwalipikadong espesyalista sa agrikultura.")),
        InfoSection(localized("Why do scan tips matter?", "Bakit mahalaga ang payo sa scan?"), localized("Lighting, focus, distance, and motion can change preview confidence and the quality of the still image you capture.", "Maaaring baguhin ng ilaw, focus, layo, at paggalaw ang kumpiyansa ng preview at kalidad ng still image na kukunin mo.")),
        InfoSection(localized("How do I change language or theme?", "Paano palitan ang wika o tema?"), localized("Open Settings and choose Language or Theme. These selections are saved locally.", "Buksan ang Mga Setting at piliin ang Wika o Tema. Lokal itong nase-save.")),
    ),
    onBack = onBack,
)

@Composable
fun AboutScreen(onBack: () -> Unit) = InformationScreen(
    title = stringResource(R.string.about_app),
    introduction = localized("Eggplant Disease Detector • Version ${BuildConfig.VERSION_NAME}", "Tagatukoy ng Sakit ng Talong • Bersyon ${BuildConfig.VERSION_NAME}"),
    sections = listOf(
        InfoSection(localized("Purpose", "Layunin"), localized("An offline Android disease detector developed for an eggplant thesis project.", "Isang offline Android disease detector para sa tesis tungkol sa talong.")),
        InfoSection(localized("Technology", "Teknolohiya"), localized("Built with Kotlin, Jetpack Compose, CameraX, NCNN, and Room/SQLite.", "Binuo gamit ang Kotlin, Jetpack Compose, CameraX, NCNN, at Room/SQLite.")),
        InfoSection(localized("Current scope", "Kasalukuyang saklaw"), localized("The model maps ten classes: eight eggplant conditions plus Healthy Leaf and Healthy Plant.", "Sampung class ang mina-map ng model: walong kondisyon ng talong, Healthy Leaf, at Healthy Plant.")),
        InfoSection(localized("Privacy", "Pagkapribado"), localized("No account, backend, Internet permission, advertising, or analytics is included.", "Walang account, backend, pahintulot sa Internet, patalastas, o analytics.")),
        InfoSection(localized("Important notice", "Mahalagang paalala"), localized("Results and agricultural guidance do not replace laboratory confirmation or advice from a qualified agricultural specialist.", "Hindi pamalit sa laboratory confirmation o payo ng kwalipikadong espesyalista ang resulta at gabay.")),
        InfoSection(localized("Open-source notices", "Mga abiso sa open source"), localized("AndroidX, Jetpack Compose, Kotlin, and Room are used under their respective licenses.", "Ginagamit ang AndroidX, Jetpack Compose, Kotlin, at Room sa ilalim ng kani-kanilang lisensya.")),
    ),
    onBack = onBack,
)

@Composable
fun OfflineStatusScreen(onBack: () -> Unit) = InformationScreen(
    title = stringResource(R.string.offline_use),
    introduction = localized("The application is designed to operate without an Internet connection.", "Dinisenyo ang app upang gumana nang walang koneksyon sa Internet."),
    sections = listOf(
        InfoSection(localized("What works offline", "Gumagana offline"), localized("Hold-to-preview live assistance, still-image capture analysis, Gallery analysis, disease library, local history, notifications, settings, and support pages.", "Hold-to-preview live assistance, still-image capture analysis, Gallery analysis, aklatan ng sakit, lokal na kasaysayan, abiso, setting, at mga pahina ng tulong.")),
        InfoSection(localized("Model status", "Katayuan ng modelo"), localized("YOLO26m FP16 inference runs on-device through NCNN; images and detections are not sent to a server.", "On-device na tumatakbo sa NCNN ang YOLO26m FP16 inference; hindi ipinapadala sa server ang larawan o detection.")),
        InfoSection(localized("No background transfer", "Walang lihim na paglilipat"), localized("There is no account, synchronization service, analytics SDK, or network permission.", "Walang account, sync service, analytics SDK, o pahintulot sa network.")),
    ),
    onBack = onBack,
)

@Composable
private fun localized(english: String, filipino: String): String {
    val language = LocalConfiguration.current.locales[0].language
    return if (language == "fil" || language == "tl") filipino else english
}
