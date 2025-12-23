package com.feng.socketdemo.ui.main;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.feng.socketdemo.R;
import com.feng.socketdemo.base.BaseBindingActivity;
import com.feng.socketdemo.bean.DataItem;
import com.feng.socketdemo.databinding.ActivityAlbumBinding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AlbumActivity extends BaseBindingActivity<ActivityAlbumBinding, AlbumModel> {

    private AlbumAdapter mainAdapter;
    private List<DataItem> dataList = new ArrayList<>();

    @Override
    protected int getLayoutId() {
        return R.layout.activity_album;
    }

    @Override
    protected void initView() {
        initRecyclerView();
    }


    @Override
    protected void initData() {
        initDataList();

    }

    @Override
    protected void initObserver() {

    }

    private void initDataList() {
        // 模拟数据 - 第一组
        List<String> imageUrls1 = Arrays.asList(
                "https://example.com/image1.jpg",
                "https://example.com/image2.jpg",
                "https://example.com/image3.jpg",
                "https://example.com/image4.jpg"
        );
        DataItem item1 = new DataItem("2025-01-09", "15:21:46", imageUrls1);

        // 模拟数据 - 第二组
        List<String> imageUrls2 = Arrays.asList(
                "https://example.com/image5.jpg",
                "https://example.com/image6.jpg",
                "https://example.com/image7.jpg"
        );
        DataItem item2 = new DataItem("2024-12-26", "15:15:22", imageUrls2);

        // 模拟数据 - 第三组
        List<String> imageUrls3 = Arrays.asList(
                "https://example.com/image8.jpg",
                "https://example.com/image9.jpg"
        );
        DataItem item3 = new DataItem("2024-12-26", "14:36:06", imageUrls3);

        // 添加数据
        dataList.add(item1);
        dataList.add(item2);
        dataList.add(item3);

        // 通知数据更新
        mainAdapter.notifyDataSetChanged();
    }

    private void initRecyclerView() {
        // 设置布局管理器
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        binding.rvMainList.setLayoutManager(layoutManager);

        // 初始化适配器
        mainAdapter = new AlbumAdapter(dataList);
        binding. rvMainList.setAdapter(mainAdapter);
    }

}
