package com.myjpa.demo.entity;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Immutable
@Table(name="student_view")
@Subselect("SELECT p.id AS id, p.name AS name, s.branch AS branch, s.marks AS marks FROM people p JOIN student s ON p.id=s.id")
public class StudentView {
	@Id
	private int id;
	
	private String name;
	
	private String branch;
	private String marks;
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getBranch() {
		return branch;
	}
	public void setBranch(String branch) {
		this.branch = branch;
	}
	public String getMarks() {
		return marks;
	}
	public void setMarks(String marks) {
		this.marks = marks;
	}
}
