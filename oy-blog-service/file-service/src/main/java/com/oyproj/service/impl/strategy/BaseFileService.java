package com.oyproj.service.impl.strategy;

import com.oyproj.base.BaseUpload;
import com.oyproj.constant.StoragePlatformEnum;
import com.oyproj.domain.model.StorageDomain;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class BaseFileService extends BaseUpload {

    private final Map<StoragePlatformEnum, BaseUpload> uploadStrategyMap = new ConcurrentHashMap<>();

    public BaseFileService(StorageDomain storage, List<BaseUpload> uploadList){
        super(storage);
        // 初始化策略 Map
        if (uploadList != null) {
            for (BaseUpload upload : uploadList) {
                // 排除自己，避免循环引用
                if (upload instanceof BaseFileService) {
                    continue;
                }
                uploadStrategyMap.put(upload.getPlatform(), upload);
            }
        }
    }


    @Override
    public StoragePlatformEnum getPlatform() {
        return null;
    }

    @Override
    public String upload(InputStream inputStream, String path, String contentType) {
        return "";
    }

    @Override
    public boolean delete(String path) {
        return false;
    }

    @Override
    public String getUrl(String path) {
        return "";
    }

    @Override
    public boolean exists(String path) {
        return false;
    }
}
