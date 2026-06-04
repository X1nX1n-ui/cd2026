package com.cd.server.impl;

import com.cd.entity.Test;
import com.cd.exception.ResourceNotFoundException;
import com.cd.mapper.TestMapper;
import com.cd.server.TestServer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TestServerImpl implements TestServer {

    private final TestMapper testMapper;

    public TestServerImpl(TestMapper testMapper) {
        this.testMapper = testMapper;
    }

    @Override
    public List<Test> list() {
        return testMapper.selectList();
    }

    @Override
    public Test getById(Long id) {
        Test test = testMapper.selectById(id);
        if (test == null) {
            throw new ResourceNotFoundException("test not found, id=" + id);
        }
        return test;
    }

    @Override
    @Transactional
    public Test create(Test test) {
        testMapper.insert(test);
        return getById(test.getId());
    }

    @Override
    @Transactional
    public Test update(Test test) {
        int rows = testMapper.update(test);
        if (rows == 0) {
            throw new ResourceNotFoundException("test not found, id=" + test.getId());
        }
        return getById(test.getId());
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        int rows = testMapper.deleteById(id);
        if (rows == 0) {
            throw new ResourceNotFoundException("test not found, id=" + id);
        }
    }
}
