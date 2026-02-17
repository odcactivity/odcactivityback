package com.odk.Repository;

import com.odk.Entity.ReponseCourrier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReponseCourrierRepository extends JpaRepository<ReponseCourrier, Long> {

    List<ReponseCourrier> findByCourrierIdOrderByDateReponseDesc(Long courrierId);

    @Query("SELECT r FROM ReponseCourrier r WHERE r.courrier.id = :courrierId ORDER BY r.dateReponse DESC")
    List<ReponseCourrier> findReponsesByCourrierId(@Param("courrierId") Long courrierId);

    boolean existsByCourrierIdAndEmail(Long courrierId, String email);

    @Query("SELECT COUNT(r) > 0 FROM ReponseCourrier r WHERE r.courrier.id = :courrierId AND r.email = :email")
    boolean hasUserResponded(@Param("courrierId") Long courrierId, @Param("email") String email);
}
