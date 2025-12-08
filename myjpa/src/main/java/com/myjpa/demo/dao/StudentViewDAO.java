package com.myjpa.demo.dao;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import com.myjpa.demo.entity.StudentView;

@NoRepositoryBean
public interface StudentViewDAO extends JpaRepository<StudentView,Integer> {

}
