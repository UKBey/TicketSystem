package com.ticketsystem.it_service_backend.repository;

import com.ticketsystem.it_service_backend.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}