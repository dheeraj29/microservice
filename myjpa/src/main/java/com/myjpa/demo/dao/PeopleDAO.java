package com.myjpa.demo.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.myjpa.demo.entity.People;

@Repository
public interface PeopleDAO extends JpaRepository<People,Integer> {
}
