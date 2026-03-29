package com.sanitary.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sanitary.admin.entity.Material;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MaterialMapper extends BaseMapper<Material> {
    /** 忽略软删除标记，按编码查找（用于恢复已删除记录） */
    @Select("SELECT * FROM material WHERE material_code = #{code} LIMIT 1")
    Material findByCodeIgnoreDeleted(@Param("code") String code);

    /** 恢复软删除记录并更新全字段 */
    @Update("UPDATE material SET deleted=0, material_name=#{m.materialName}, spec=#{m.spec}, " +
            "customer_id=#{m.customerId}, customer_name=#{m.customerName}, " +
            "default_price=#{m.defaultPrice}, unit=#{m.unit}, status=#{m.status}, update_time=NOW() " +
            "WHERE id=#{m.id}")
    void restoreAndUpdate(@Param("m") Material m);
}
