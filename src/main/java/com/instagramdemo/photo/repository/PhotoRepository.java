package com.instagramdemo.photo.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

import com.instagramdemo.photo.entity.Photo;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
	List<Photo> findAllByUserIdOrderByTimestampDesc(Long userId);

	Optional<Photo> findByIdAndUserId(Long id, Long userId);
}