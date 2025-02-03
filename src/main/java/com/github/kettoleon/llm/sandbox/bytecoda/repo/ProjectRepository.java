package com.github.kettoleon.llm.sandbox.bytecoda.repo;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepositoryImplementation<Project, String> {


}
