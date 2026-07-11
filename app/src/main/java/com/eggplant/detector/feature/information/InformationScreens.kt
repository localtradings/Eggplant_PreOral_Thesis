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
    introduction = localized("Detection stays on this phone. Optional cloud features send data only after you choose to use them.", "Nananatili sa teleponong ito ang detection. Nagpapadala lamang ng datos ang opsyonal na cloud feature kapag pinili mong gamitin ito."),
    sections = listOf(
        InfoSection(localized("Offline detection", "Offline na detection"), localized("Live detection, still and Gallery analysis, the disease library, My Scans, and settings work locally without sending images to a server.", "Lokal na gumagana ang live detection, still at Gallery analysis, aklatan ng sakit, My Scans, at settings nang hindi ipinapadala sa server ang larawan.")),
        InfoSection(localized("Optional anonymous cloud", "Opsyonal na anonymous cloud"), localized("Global Scans and missing-disease requests use an anonymous Supabase identity. Explicitly shared photos and private request photos are uploaded by a queued background worker when a network is available. No name or profile is published.", "Gumagamit ang Global Scans at missing-disease request ng anonymous Supabase identity. Ang tahasang ibinahaging larawan at pribadong request photo ay ina-upload ng nakapilang background worker kapag may network. Walang pangalan o profile na inilalathala.")),
        InfoSection(localized("Local history", "Lokal na kasaysayan"), localized("Scan records, language, theme, units, and notification read state are stored in the app's local Room/SQLite database.", "Sa lokal na Room/SQLite database iniimbak ang scan, wika, tema, yunit, at nabasang abiso.")),
        InfoSection(localized("Photos", "Mga larawan"), localized("Saved scans use private app storage without EXIF or location metadata. A cloud outbox copy may remain until an approved upload succeeds or is cancelled. Public Global Scan photos expire after 180 days; disease-request photos remain private to the requester and admins.", "Gumagamit ang naka-save na scan ng pribadong app storage nang walang EXIF o lokasyon. Maaaring manatili ang cloud outbox copy hanggang magtagumpay o makansela ang inaprubahang upload. Nag-e-expire ang public Global Scan photo pagkalipas ng 180 araw; pribado sa requester at admins ang disease-request photo.")),
        InfoSection(localized("Removal", "Pag-alis"), localized("Uninstalling or clearing app storage removes local data. “Delete my shared cloud data” unpublishes your contributions and queues deletion of your anonymous cloud photos and records.", "Inaalis ng pag-uninstall o pag-clear ng app storage ang lokal na datos. Inaalis sa publikasyon ng “Delete my shared cloud data” ang kontribusyon at ipinapila ang pagbura ng anonymous cloud photos at records.")),
        InfoSection(localized("Camera permission", "Pahintulot sa kamera"), localized("Camera access is used only for still capture and optional hold-to-preview live detection. Gallery selection uses Android's system photo picker.", "Ginagamit lamang ang camera access para sa still capture at opsyonal na hold-to-preview live detection. Android system photo picker ang gamit sa Gallery.")),
    ),
    onBack = onBack,
)

@Composable
fun HelpScreen(onBack: () -> Unit) = InformationScreen(
    title = stringResource(R.string.help_faq),
    introduction = localized("Answers for the offline-first detector and its optional cloud features.", "Mga sagot para sa offline-first detector at mga opsyonal nitong cloud feature."),
    sections = listOf(
        InfoSection(localized("Does the app need Internet?", "Kailangan ba ng Internet?"), localized("Not for detection, Gallery analysis, the library, My Scans, or settings. Internet is required to refresh Global Scans, share a scan, submit a missing-disease request, report public content, or process cloud deletion.", "Hindi para sa detection, Gallery analysis, aklatan, My Scans, o settings. Kailangan ang Internet para i-refresh ang Global Scans, mag-share ng scan, magsumite ng missing-disease request, mag-report ng public content, o magproseso ng cloud deletion.")),
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
        InfoSection(localized("Purpose", "Layunin"), localized("An offline-first Android disease detector developed for an eggplant thesis project, with optional anonymous sharing and private disease requests.", "Isang offline-first Android disease detector para sa tesis tungkol sa talong, na may opsyonal na anonymous sharing at pribadong disease request.")),
        InfoSection(localized("Technology", "Teknolohiya"), localized("Built with Kotlin, Jetpack Compose, CameraX, NCNN, and Room/SQLite.", "Binuo gamit ang Kotlin, Jetpack Compose, CameraX, NCNN, at Room/SQLite.")),
        InfoSection(localized("Current scope", "Kasalukuyang saklaw"), localized("The model maps ten classes: eight eggplant conditions plus Healthy Leaf and Healthy Plant.", "Sampung class ang mina-map ng model: walong kondisyon ng talong, Healthy Leaf, at Healthy Plant.")),
        InfoSection(localized("Privacy", "Pagkapribado"), localized("There are no ads, analytics, names, or public profiles. Optional cloud features use an anonymous authenticated identity, validated server APIs, and user-controlled uploads.", "Walang ads, analytics, pangalan, o public profile. Gumagamit ang opsyonal na cloud feature ng anonymous authenticated identity, validated server APIs, at upload na kontrolado ng user.")),
        InfoSection(localized("Important notice", "Mahalagang paalala"), localized("Results and agricultural guidance do not replace laboratory confirmation or advice from a qualified agricultural specialist.", "Hindi pamalit sa laboratory confirmation o payo ng kwalipikadong espesyalista ang resulta at gabay.")),
        InfoSection(
            localized("Open-source notices", "Mga abiso sa open source"),
            localized(
                "The packaged Ultralytics YOLO model/export is identified as AGPL-3.0, and NCNN is distributed under BSD-3-Clause with additional bundled notices. Complete license text, notices, and source are published with this app at github.com/localtradings/Eggplant_Finals_Thesis.",
                "Ang kasamang Ultralytics YOLO model/export ay nakatukoy bilang AGPL-3.0, at ang NCNN ay ipinamamahagi sa ilalim ng BSD-3-Clause kasama ang iba pang abiso. Ang kumpletong lisensya, mga abiso, at source ay inilathala kasama ng app sa github.com/localtradings/Eggplant_Finals_Thesis.",
            ),
        ),
    ),
    onBack = onBack,
)

@Composable
fun OfflineStatusScreen(onBack: () -> Unit) = InformationScreen(
    title = stringResource(R.string.offline_use),
    introduction = localized("The detector is offline-first; community and request features synchronize when Internet is available.", "Offline-first ang detector; nagsi-sync ang community at request features kapag may Internet."),
    sections = listOf(
        InfoSection(localized("What works offline", "Gumagana offline"), localized("Hold-to-preview live assistance, still-image capture analysis, Gallery analysis, disease library, local history, notifications, settings, and support pages.", "Hold-to-preview live assistance, still-image capture analysis, Gallery analysis, aklatan ng sakit, lokal na kasaysayan, abiso, setting, at mga pahina ng tulong.")),
        InfoSection(localized("Model status", "Katayuan ng modelo"), localized("YOLO26m FP16 inference runs on-device through NCNN. Detection does not require cloud inference.", "On-device na tumatakbo sa NCNN ang YOLO26m FP16 inference. Hindi kailangan ng cloud inference para sa detection.")),
        InfoSection(localized("When network is used", "Kailan ginagamit ang network"), localized("After an explicit share, report, disease request, or cloud-deletion action, WorkManager retries the queued operation when connected. Cached Global Scans remain readable offline. Advertising and analytics SDKs are not included.", "Pagkatapos ng tahasang share, report, disease request, o cloud-deletion action, muling sinusubukan ng WorkManager ang nakapilang operasyon kapag may koneksyon. Nababasa offline ang cached Global Scans. Walang advertising o analytics SDK.")),
    ),
    onBack = onBack,
)

@Composable
private fun localized(english: String, filipino: String): String {
    val language = LocalConfiguration.current.locales[0].language
    return if (language == "fil" || language == "tl") filipino else english
}
