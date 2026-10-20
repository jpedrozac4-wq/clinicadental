package com.empresa.calzado.erp.soporte;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface MotivoQuejaRepository extends JpaRepository<MotivoQueja, Integer> {
    Optional<MotivoQueja> findByCode(String code);
    List<MotivoQueja> findByActiveTrue();
}
