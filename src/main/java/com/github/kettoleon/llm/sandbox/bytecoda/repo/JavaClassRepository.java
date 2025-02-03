package com.github.kettoleon.llm.sandbox.bytecoda.repo;

import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JavaClassRepository extends JpaRepositoryImplementation<JavaClass, String> {

    List<JavaClass> findAllByFile(JavaFile jf);

    Optional<JavaClass> findByFileAndQualifiedName(JavaFile jf, String cjc);
}
