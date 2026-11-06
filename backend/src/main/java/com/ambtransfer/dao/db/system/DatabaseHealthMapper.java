package com.ambtransfer.dao.db.system;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface DatabaseHealthMapper {
    @Select("SELECT DB_NAME()")
    String databaseName();
}
