package com.sanitary.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sanitary.admin.entity.Process;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProcessMapper extends BaseMapper<Process> {
    /** 忽略软删除标记，按编码查找（用于恢复已删除记录） */
    @Select("SELECT * FROM process WHERE process_code = #{code} LIMIT 1")
    Process findByCodeIgnoreDeleted(@Param("code") String code);

    /** 恢复软删除记录并更新全字段 */
    @Update("UPDATE process SET deleted=0, process_name=#{p.processName}, process_category=#{p.processCategory}, " +
            "process_nature=#{p.processNature}, thickness_req=#{p.thicknessReq}, default_quote=#{p.defaultQuote}, " +
            "priority_no=#{p.priorityNo}, remark=#{p.remark}, status=#{p.status}, update_time=NOW() " +
            "WHERE id=#{p.id}")
    void restoreAndUpdate(@Param("p") Process p);
}
