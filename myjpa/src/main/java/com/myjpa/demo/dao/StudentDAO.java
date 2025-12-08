package com.myjpa.demo.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.myjpa.demo.entity.Student;

public interface StudentDAO extends JpaRepository<Student,Integer> {
	@Query(value = "SELECT id, branch, marks FROM student_view st WHERE st.id=:id", nativeQuery=true)
	List<Student> getStudents(@Param(value = "id") int id);
}