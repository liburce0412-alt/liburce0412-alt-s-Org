package com.example.features.v2

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.core.model.SupabaseProduct
import com.example.core.network.SupabaseManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealMarketScreen(
    onNavigateToChat: (String) -> Unit
) {
    val context = LocalContext.current
    val productsState by SupabaseManager.products.collectAsStateWithLifecycle()
    val favoritesState by SupabaseManager.favorites.collectAsStateWithLifecycle()
    
    var selectedProduct by remember { mutableStateOf<SupabaseProduct?>(null) }
    var showCreateProductDialog by remember { mutableStateOf(false) }
    var activeCategoryFilter by remember { mutableStateOf("全部") }

    val categories = listOf("全部", "教材书籍", "电子数码", "生活文具", "体育器材")

    // Filtered Products List (excluding approved out items easily if required, and taking down product checks)
    val filteredProducts = remember(productsState, activeCategoryFilter) {
        val listed = productsState.filter { it.status != "hidden" }
        if (activeCategoryFilter == "全部") listed else {
            listed.filter { it.category == activeCategoryFilter }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                // Category tabs filtration row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach { cat ->
                        val isSelected = activeCategoryFilter == cat
                        Button(
                            onClick = { activeCategoryFilter = cat },
                            shape = RoundedCornerShape(100),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text(
                                cat,
                                fontSize = 11.sp,
                                color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateProductDialog = true },
                shape = RoundedCornerShape(100),
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                icon = { Icon(Icons.Default.Storefront, "sell") },
                text = { Text("我要闲置", fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .testTag("publish_product_fab")
            )
        }
    ) { paddingValues ->
        if (filteredProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storefront, "empty", modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("该类别下暂无挂售商品", color = MaterialTheme.colorScheme.outline, fontSize = 13.sp)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                items(filteredProducts, key = { it.id }) { prod ->
                    ProductCardGridItem(
                        product = prod,
                        onClick = { selectedProduct = prod }
                    )
                }
            }
        }
    }

    // Interactive Multi step product detail overlay
    selectedProduct?.let { prod ->
        ProductDetailsOverlay(
            product = prod,
            onDismiss = { selectedProduct = null },
            onNavigateToChat = { partnerId ->
                selectedProduct = null
                onNavigateToChat(partnerId)
            }
        )
    }

    // Create New Product Dialogue Dialog
    if (showCreateProductDialog) {
        CreateProductDialog(onDismiss = { showCreateProductDialog = false })
    }
}

@Composable
fun ProductCardGridItem(
    product: SupabaseProduct,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f), RoundedCornerShape(18.dp))
    ) {
        Column {
            Box {
                AsyncImage(
                    model = product.imageUrl.ifEmpty { "https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&q=80&w=200" },
                    contentDescription = "product",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                )
                
                // Status Badge Overlay
                if (product.status != "pending") {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(
                                color = if (product.status == "sold") Color.Gray else Color(0xFFE2B911),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (product.status == "sold") "已售磬" else "抢购面交中",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = product.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "￥${String.format("%.1f", product.price)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = product.category,
                        color = MaterialTheme.colorScheme.outline,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

// Full interactive product Details Overlay dialogue screen
@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ProductDetailsOverlay(
    product: SupabaseProduct,
    onDismiss: () -> Unit,
    onNavigateToChat: (String) -> Unit
) {
    val context = LocalContext.current
    var showBuyConfirm by remember { mutableStateOf(false) }
    
    val isFavorited = SupabaseManager.isProductFavorited(product.id)
    val me = SupabaseManager.currentUser.value

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .height(490.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Main Graphic with Back control
                Box(modifier = Modifier.height(180.dp).fillMaxWidth()) {
                    AsyncImage(
                        model = product.imageUrl.ifEmpty { "https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&q=80&w=300" },
                        contentDescription = "banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "close", tint = Color.White)
                    }
                }

                // Details content layout
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "￥${String.format("%.1f", product.price)}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Box(
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(product.category, fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = product.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = product.description,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    // Seller info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                            .padding(8.dp)
                    ) {
                        AsyncImage(model = product.sellerAvatar, contentDescription = "seller", modifier = Modifier.size(34.dp).clip(CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(product.sellerName, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("信用评级: 优秀学术个人", fontSize = 10.sp, color = Color.Gray)
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

                // Action Row (touch target size >= 48dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Favorite Toggle Control (加心/未加心)
                    IconButton(
                        onClick = {
                            SupabaseManager.toggleProductFavorite(product.id)
                            Toast.makeText(context, if (isFavorited) "已取消收藏该物品" else "成功加入您的心愿单！", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .size(48.dp)
                            .testTag("favorite_product_btn")
                    ) {
                        Icon(
                            imageVector = if (isFavorited) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "favorite",
                            tint = if (isFavorited) Color.Red else Color.Gray,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Contact Chat seller button
                    if (product.sellerId != me?.id) {
                        Button(
                            onClick = { onNavigateToChat(product.sellerId) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            contentPadding = PaddingValues(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("chat_seller_btn")
                        ) {
                            Icon(Icons.Filled.Forum, "chat", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("联系卖家聊聊", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Buy Now Capsule action button
                    if (product.status == "pending" && product.sellerId != me?.id) {
                        Button(
                            onClick = { showBuyConfirm = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(14.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                                .testTag("buy_product_btn")
                        ) {
                            Icon(Icons.Filled.ElectricBolt, "buy", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("一秒淘下抢购", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {},
                            enabled = false,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .height(48.dp)
                        ) {
                            Text(
                                text = if (product.sellerId == me?.id) "您发布的宝贝" else "宝贝正在出售",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // One-click Buy Order confirm prompt
    if (showBuyConfirm) {
        AlertDialog(
            onDismissRequest = { showBuyConfirm = false },
            title = { Text("确认一键拍下该闲置商品？", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White) },
            text = {
                Text(
                    "拍下后系统将立即锁定库存并生成面交订单。请尽快通过“联系卖家”约定校内具体交收地点。",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        SupabaseManager.buyProduct(product.id)
                        showBuyConfirm = false
                        Toast.makeText(context, "抢购订单已生成！正在通过私信通知卖家线下交付...", Toast.LENGTH_LONG).show()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("确认拍下", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBuyConfirm = false }) {
                    Text("放弃")
                }
            },
            containerColor = Color(0xFF1E2026),
            textContentColor = Color.White,
            titleContentColor = Color.White
        )
    }
}

// Create Product Dialogue Dialog screen
@Composable
fun CreateProductDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf("") }
    var priceStr by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("教材书籍") }

    val categories = listOf("教材书籍", "电子数码", "生活文具", "体育器材")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("发布闲置宝贝", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("商品名称 (如: 考研数学复习全书)", fontSize = 13.sp) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("new_product_title")
                )

                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    placeholder = { Text("转手价格 ￥ (面交更优惠)", fontSize = 13.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("new_product_price")
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    placeholder = { Text("描述宝贝成色、购买渠道、校内面交偏好等...", fontSize = 13.sp) },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Text("选择宝贝大类：", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { c ->
                        val isSel = selectedCategory == c
                        FilterChip(
                            selected = isSel,
                            onClick = { selectedCategory = c },
                            label = { Text(c, fontSize = 10.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val priceVal = priceStr.toDoubleOrNull()
                    if (title.isEmpty() || priceVal == null || priceVal <= 0f) {
                        Toast.makeText(context, "请输入合法的转手宝贝名称及价格！", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    val defaultSamplePic = when (selectedCategory) {
                        "教材书籍" -> "https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&q=80&w=200"
                        "电子数码" -> "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&q=80&w=200"
                        "体育器材" -> "https://images.unsplash.com/photo-1517649763962-0c623066013b?auto=format&fit=crop&q=80&w=200"
                        else -> "https://images.unsplash.com/photo-1456513080510-7bf3a84b82f8?auto=format&fit=crop&q=80&w=200"
                    }
                    SupabaseManager.publishProduct(
                        title = title,
                        description = description,
                        price = priceVal,
                        originalPrice = priceVal * 1.5,
                        category = selectedCategory,
                        condition = "全新闲置",
                        location = "西校区宿区面交",
                        imageUrl = defaultSamplePic
                    )
                    Toast.makeText(context, "闲置商品上架挂售成功！", Toast.LENGTH_SHORT).show()
                    onDismiss()
                }
            ) {
                Text("发布上架宝贝")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("下架/取消")
            }
        }
    )
}
