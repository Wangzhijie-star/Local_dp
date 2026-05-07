package com.hmdp.mapper;

import com.hmdp.entity.ShopType;

import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface ShopTypeMapper extends BaseMapper<ShopType> {
    /**
     * 查询所有店铺类型
     * @return 所有店铺类型
     */
    List<ShopType> selectList();

}
