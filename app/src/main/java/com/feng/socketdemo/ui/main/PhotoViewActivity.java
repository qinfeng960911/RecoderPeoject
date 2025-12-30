package com.feng.socketdemo.ui.main;

import androidx.viewpager2.widget.ViewPager2;

import com.feng.socketdemo.R;
import com.feng.socketdemo.base.BaseActivity;
import com.feng.socketdemo.databinding.ActivityPhotoViewerBinding;

import java.util.ArrayList;
import java.util.List;

public class PhotoViewActivity extends BaseActivity<ActivityPhotoViewerBinding, PhotoViewModel> {

    private final String TAG = "PhotoViewActivity";
    private ViewPager2 viewPager;
    private PhotoViewPagerAdapter adapter;
    private List<String> imageUrls = new ArrayList<>();
    private int currentPosition = 0;

    @Override
    protected int getLayoutId() {
        return R.layout.activity_photo_viewer;
    }

    @Override
    protected void initView() {
        viewPager = binding.viewPager;


        initViewPager();

        // 设置监听器
        setupListeners();
    }

    @Override
    protected void initData() {
        String imageUrl = "https://image.baidu.com/search/detail?ct=503316480&z=0&tn=baiduimagedetail&ipn=d&cl=2&cm=1&sc=0&sa=vs_ala_img_datu&lm=-1&ie=utf8&pn=2&rn=1&di=7565560840087142401&ln=0&word=%E7%BE%8E%E5%A5%B3%E5%9B%BE%E7%89%87&os=3633436566,3585831990&cs=3562167861,267882055&objurl=http%3A%2F%2Fimage109.360doc.com%2FDownloadImg%2F2024%2F02%2F1109%2F279562181_2_20240211091336301&bdtype=0&simid=3562167861,267882055&pi=0&adpicid=0&timingneed=&spn=0&is=0,0&lid=d293379a003f02ae";
        // 模拟数据
        imageUrls.add(imageUrl);
        imageUrls.add(imageUrl);
        imageUrls.add(imageUrl);
        imageUrls.add(imageUrl);
        imageUrls.add(imageUrl);

        // 获取传递的位置
        currentPosition = getIntent().getIntExtra("position", 0);

        // 更新适配器
        adapter.notifyDataSetChanged();

        // 设置当前页面
        viewPager.setCurrentItem(currentPosition, false);

        // 更新索引显示
        updateIndexText();
    }

    @Override
    protected void initObserver() {

    }

    private void initViewPager() {
        // 设置ViewPager2
        viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        // 设置适配器
        adapter = new PhotoViewPagerAdapter(imageUrls);
        viewPager.setAdapter(adapter);

        // 设置图片点击监听
        adapter.setOnPhotoClickListener(position -> {

        });
    }


    private void setupListeners() {
        // 返回按钮
        binding.ivBack.setOnClickListener(v -> finish());

        // ViewPager页面变化监听
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                currentPosition = position;
                updateIndexText();
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                // 可以在这里处理滚动状态
            }

            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
                // 可以在这里处理滚动过程
            }
        });
    }

    private void updateIndexText() {
        int total = adapter.getItemCount();
        int current = currentPosition + 1;
        binding.tvIndex.setText(current + "/" + total);
    }

}
