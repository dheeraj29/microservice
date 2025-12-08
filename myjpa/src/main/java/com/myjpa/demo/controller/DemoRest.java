package com.myjpa.demo.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.myjpa.demo.dao.PeopleDAO;
import com.myjpa.demo.dao.StudentDAO;
import com.myjpa.demo.entity.People;
import com.myjpa.demo.entity.Student;

@RestController
@RequestMapping("/api")
public class DemoRest {
	
	@Autowired
	private PeopleDAO peopleDAO;
	
	@Autowired
	private StudentDAO studentDAO;
	
	@GetMapping("/hello")
	public void hello() {
		Student student = new Student();
		student.setId(1);
		student.setName("dheeraj");
		student.setMarks("100");
		student.setBranch("BVR");
		studentDAO.save(student);
		Student student1 = new Student();
		student1.setId(2);
		student1.setName("hani");
		student1.setMarks("100");
		student1.setBranch("GUN");
		studentDAO.save(student1);
		People people1 = new People();
		people1.setId(3);
		people1.setName("lalli");
		peopleDAO.save(people1);
		List<Student> studentDAO1 = studentDAO.getStudents(2);
		System.out.print("Result obtained "+studentDAO1.size());
	}
	
}
