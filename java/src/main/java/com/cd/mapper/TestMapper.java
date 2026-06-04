package com.cd.mapper;

import com.cd.entity.Test;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TestMapper {

    List<Test> selectList();

    Test selectById(@Param("id") Long id);

    int insert(Test test);

    int update(Test test);

    int deleteById(@Param("id") Long id);
}
