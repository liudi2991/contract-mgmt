package com.company.contract.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {
    private long total;
    private List<T> records;

    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getTotal(), page.getRecords());
    }

    public static <T, R> PageResult<R> of(IPage<T> page, java.util.function.Function<T, R> mapper) {
        List<R> list = page.getRecords().stream().map(mapper).toList();
        return new PageResult<>(page.getTotal(), list);
    }
}
