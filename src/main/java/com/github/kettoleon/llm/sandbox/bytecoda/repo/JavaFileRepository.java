package com.github.kettoleon.llm.sandbox.bytecoda.repo;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JavaFileRepository extends JpaRepositoryImplementation<JavaFile, String> {

    Optional<JavaFile> findByProjectAndPath(Project project, String string);

    List<JavaFile> findAllByProject(Project project);
}
