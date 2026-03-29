package com.sanitary.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sanitary.admin.entity.Customer;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CustomerMapper extends BaseMapper<Customer> {
    /** 忽略软删除标记，按编码查找（用于恢复已删除记录） */
    @Select("SELECT * FROM customer WHERE customer_code = #{code} LIMIT 1")
    Customer findByCodeIgnoreDeleted(@Param("code") String code);

    /** 恢复软删除记录并更新全字段 */
    @Update("UPDATE customer SET deleted=0, customer_name=#{c.customerName}, customer_type=#{c.customerType}, " +
            "address=#{c.address}, contact_person=#{c.contactPerson}, contact_phone=#{c.contactPhone}, " +
            "salesperson=#{c.salesperson}, bank_name=#{c.bankName}, bank_account=#{c.bankAccount}, " +
            "tax_no=#{c.taxNo}, remark=#{c.remark}, status=#{c.status}, update_time=NOW() " +
            "WHERE id=#{c.id}")
    void restoreAndUpdate(@Param("c") Customer c);
}
