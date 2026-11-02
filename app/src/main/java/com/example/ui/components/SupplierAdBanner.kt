package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.data.remote.PublicSupplierAd
import com.example.data.remote.PublicSupplierAds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun SupplierAdBanner() {
    var ads by remember { mutableStateOf<List<PublicSupplierAd>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (isActive) {
            ads = try { PublicSupplierAds.load() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() }
            delay(60000)
        }
    }
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ads.forEach { ad ->
            ElevatedCard(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Supplier advertisement · ${ad.businessName}", style = MaterialTheme.typography.labelSmall)
                    Text(ad.title, style = MaterialTheme.typography.titleMedium)
                    if (ad.description.isNotBlank()) Text(ad.description, style = MaterialTheme.typography.bodyMedium)
                    AsyncImage(
                        model = ImageRequest.Builder(context).data(ad.artworkUrl).memoryCachePolicy(CachePolicy.DISABLED).diskCachePolicy(CachePolicy.DISABLED).build(),
                        contentDescription = "${ad.businessName}: ${ad.title}",
                        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp, max = 210.dp),
                        contentScale = ContentScale.Fit,
                        onError = { ads = ads.filterNot { it.id == ad.id } }
                    )
                }
            }
        }
    }
}
