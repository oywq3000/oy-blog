package com.oyproj.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话重命名请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RenameRequest {

    /**
     * 新标题
     */
    private String title;
}
