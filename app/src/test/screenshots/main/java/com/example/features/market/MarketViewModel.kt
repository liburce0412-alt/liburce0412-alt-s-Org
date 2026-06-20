package com.example.features.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.database.CampusDao
import com.example.core.database.GoodsEntity
import com.example.core.model.Goods
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MarketViewModel(private val dao: CampusDao) : ViewModel() {

    // Complete raw items from local Room flow
    val allGoods: StateFlow<List<Goods>> = dao.getAllGoodsFlow()
        .map { list -> list.map { it.toDomain() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedTabFilter = MutableStateFlow("all") // "all", "favorite"
    val selectedTabFilter: StateFlow<String> = _selectedTabFilter.asStateFlow()

    // Filtered list of goods based on live search keywords & active tab
    val filteredGoods: StateFlow<List<Goods>> = combine(allGoods, _searchQuery, _selectedTabFilter) { goods, query, filter ->
        var list = goods
        if (filter == "favorite") {
            list = list.filter { it.isFavorite }
        }
        if (query.isNotEmpty()) {
            list = list.filter {
                it.title.contains(query, ignoreCase = true) || 
                it.description.contains(query, ignoreCase = true)
            }
        }
        list
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterTab(tab: String) {
        _selectedTabFilter.value = tab
    }

    fun publishItem(
        title: String,
        description: String,
        price: Double,
        imageUrl: String,
        sellerName: String
    ) {
        viewModelScope.launch {
            val newItem = Goods(
                title = title,
                description = description,
                price = price,
                imageUrl = imageUrl.ifEmpty { "https://images.unsplash.com/photo-1543002588-bfa74002ed7e?auto=format&fit=crop&q=80&w=400" },
                sellerName = sellerName
            )
            dao.insertGoods(GoodsEntity.fromDomain(newItem))
        }
    }

    fun toggleFavorite(item: Goods) {
        viewModelScope.launch {
            dao.updateGoodsFavorite(item.id, !item.isFavorite)
        }
    }

    fun deleteItem(id: Int) {
        viewModelScope.launch {
            dao.deleteGoodsById(id)
        }
    }

    // Populate pre-loaded items so when a freshman opens the app, the shelf isn't blank
    fun seedSampleGoodsIfEmpty() {
        viewModelScope.launch {
            dao.getAllGoodsFlow().map { it.size }.collect { count ->
                if (count == 0) {
                    val samples = listOf(
                        GoodsEntity(
                            title = "考研数学张宇1000题",
                            description = "全新未拆封，买多了出一本。含全部精选解析，学长考上岸福利送考研资料！",
                            imageUrl = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&q=80&w=400",
                            price = 15.0,
                            sellerName = "李学长",
                            sellerId = "seller_li",
                            createdAt = System.currentTimeMillis() - 10000000,
                            isFavorite = false
                        ),
                        GoodsEntity(
                            title = "第九成新iPad Air 4 (64G)",
                            description = "平时仅用于手写记笔记和看网课，屏幕完美无划痕，送Apple Pencil一代保护套！",
                            imageUrl = "https://images.unsplash.com/photo-1544244015-0df4b3ffc6b0?auto=format&fit=crop&q=80&w=400",
                            price = 1800.0,
                            sellerName = "王学姐",
                            sellerId = "seller_wang",
                            createdAt = System.currentTimeMillis() - 5000000,
                            isFavorite = false
                        ),
                        GoodsEntity(
                            title = "人体工学电脑支撑架",
                            description = "宿舍写代码神器！六档高度调节，铝合金轻巧便携散热快，几乎没有磨损痕迹。",
                            imageUrl = "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?auto=format&fit=crop&q=80&w=400",
                            price = 28.0,
                            sellerName = "字节跳动预备猿",
                            sellerId = "seller_dev",
                            createdAt = System.currentTimeMillis(),
                            isFavorite = false
                        )
                    )
                    samples.forEach { dao.insertGoods(it) }
                }
            }
        }
    }
}

class MarketViewModelFactory(private val dao: CampusDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MarketViewModel::class.java)) {
            return MarketViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
