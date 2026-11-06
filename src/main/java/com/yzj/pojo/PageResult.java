package com.yzj.pojo;

import lombok.Data;

import java.util.List;

/**
 * 通用分页结果。
 *
 * 为什么返回"结果 + 总数 + 页码"而不是只返回数组：
 * 前端要渲染分页条，必须知道总数才能算出总页数；
 * 而 total 只能由一次 count 查询得到，列表查询本身给不出。
 *
 * pages / hasNext / hasPrev 是服务端算好一起返回的，
 * 目的是让前端不必重复实现取整和边界判断 —— 两边各算一次迟早会不一致。
 *
 * @param <T> 列表元素类型
 */
@Data
public class PageResult<T> {

    /** 当前页数据 */
    private List<T> records;

    /** 满足条件的总条数（不是当前页条数） */
    private long total;

    /** 当前页码，从 1 开始 */
    private int pageNum;

    /** 每页条数（已按上限裁剪过的实际值） */
    private int pageSize;

    /** 总页数 */
    private long pages;

    /** 是否还有下一页 */
    private boolean hasNext;

    /** 是否还有上一页 */
    private boolean hasPre;

    public static <T> PageResult<T> of(List<T> records, long total, int pageNum, int pageSize) {
        PageResult<T> page = new PageResult<>();
        page.records = records == null ? List.of() : records;
        page.total = total;
        page.pageNum = pageNum;
        page.pageSize = pageSize;
        page.pages = pageSize <= 0 ? 0 : (total + pageSize - 1) / pageSize;
        page.hasPre = pageNum > 1;
        page.hasNext = pageNum < page.pages;
        return page;
    }

    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return of(List.of(), 0, pageNum, pageSize);
    }
}
