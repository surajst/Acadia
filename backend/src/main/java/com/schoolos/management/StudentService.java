package com.schoolos.management;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;

@Service
public class StudentService {

    private final StudentRepository studentRepository;

    public StudentService(StudentRepository studentRepository) {
        this.studentRepository = studentRepository;
    }

    public List<Student> findByClassSectionIn(List<ClassSection> classSections) {
        if (classSections == null || classSections.isEmpty()) {
            return Collections.emptyList();
        }
        return studentRepository.findByClassSectionIn(classSections);
    }

    public Page<Student> findByClassSectionIn(List<ClassSection> classSections, Pageable pageable) {
        if (classSections == null || classSections.isEmpty()) {
            return Page.empty(pageable);
        }
        return studentRepository.findByClassSectionIn(classSections, pageable);
    }
}
