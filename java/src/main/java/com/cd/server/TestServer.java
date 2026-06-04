package com.cd.server;

import com.cd.entity.Test;

import java.util.List;

public interface TestServer {

    List<Test> list();

    Test getById(Long id);

    Test create(Test test);

    Test update(Test test);

    void deleteById(Long id);
}
