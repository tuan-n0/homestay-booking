package com.homestay.settings;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OperatingPolicyRepository extends JpaRepository<OperatingPolicy, Integer> {

    Optional<OperatingPolicy> findFirstByEffectiveFromLessThanEqualOrderByEffectiveFromDescIdDesc(Instant at);

    List<OperatingPolicy> findTop20ByOrderByEffectiveFromDescIdDesc();
}
