package com.cd.controller;

import com.cd.entity.Test;
import com.cd.server.TestServer;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private final TestServer testServer;

    public TestController(TestServer testServer) {
        this.testServer = testServer;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('sys:test:manage')")
    public List<Test> list() {
        return testServer.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:test:manage')")
    public Test getById(@PathVariable Long id) {
        return testServer.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('sys:test:manage')")
    @ResponseStatus(HttpStatus.CREATED)
    public Test create(@RequestBody Test test) {
        return testServer.create(test);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:test:manage')")
    public Test update(@PathVariable Long id, @RequestBody Test test) {
        test.setId(id);
        return testServer.update(test);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('sys:test:manage')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        testServer.deleteById(id);
    }
}
