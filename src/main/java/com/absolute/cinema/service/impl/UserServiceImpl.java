package com.absolute.cinema.service.impl;

import com.absolute.cinema.common.exception.custom.BadRequestException;
import com.absolute.cinema.common.exception.custom.NotFoundException;
import com.absolute.cinema.dto.UpdateUserDTO;
import com.absolute.cinema.dto.UserDTO;
import com.absolute.cinema.entity.User;
import com.absolute.cinema.mapper.UserMapper;
import com.absolute.cinema.repository.UserRepository;
import com.absolute.cinema.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;
    private static final String EMAIL_REGEX = "[a-zA-Z0-9+._%-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}";
    private static final int MIN_AGE = 13;
    private static final int MAX_AGE = 120;
    private static final String VALID_GENDERS = "MALE,FEMALE,OTHER";


    @Override
    public UserDTO getUserById(UUID id) {

        User user = userRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("User with id: %s not found", id))
        );

        return userMapper.toDTO(user);
    }

    @Override
    public UserDTO updateUser(UUID id, UpdateUserDTO updateUserDTO) {
        // Extract email from DTO
        String emailToUpdate = updateUserDTO.email();
        // Extract first name from DTO
        String firstNameToUpdate = updateUserDTO.firstName();
        // Extract last name from DTO
        String lastNameToUpdate = updateUserDTO.lastName();
        // Extract age from DTO
        int ageToUpdate = updateUserDTO.age();
        // Extract gender from DTO
        User.Gender genderToUpdate = updateUserDTO.gender();
        
        // Check if email is null or empty
        if (emailToUpdate == null || emailToUpdate.isEmpty()) {
            throw new BadRequestException("Email cannot be empty");
        }
        
        // Validate email format - must contain @ and .
        if (!emailToUpdate.contains("@") || !emailToUpdate.contains(".")) {
            throw new BadRequestException("Invalid email format");
        }
        
        // Check if age is in valid range between MIN_AGE and MAX_AGE
        if (ageToUpdate < MIN_AGE || ageToUpdate > MAX_AGE) {
            throw new BadRequestException("Age must be between " + MIN_AGE + " and " + MAX_AGE);
        }
        
        // Validate first name is not empty
        if (firstNameToUpdate == null || firstNameToUpdate.isEmpty()) {
            throw new BadRequestException("First name cannot be empty");
        }
        
        // Check first name length is between 2 and 100 characters
        if (firstNameToUpdate.length() > 100 || firstNameToUpdate.length() < 2) {
            throw new BadRequestException("First name must be between 2 and 100 characters");
        }
        
        // Validate last name is not empty
        if (lastNameToUpdate == null || lastNameToUpdate.isEmpty()) {
            throw new BadRequestException("Last name cannot be empty");
        }
        
        // Check last name length is between 2 and 100 characters
        if (lastNameToUpdate.length() > 100 || lastNameToUpdate.length() < 2) {
            throw new BadRequestException("Last name must be between 2 and 100 characters");
        }

        // Find user by ID, throw exception if not found
        User user = userRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("User with id: %s not found", id))
        );

        // Check if email already exists and is different from current user's email
        if (userRepository.existsByEmail(emailToUpdate) && !user.getEmail().equals(emailToUpdate)) {
            throw new BadRequestException("Email is already in use");
        }
        
        String currentEmail = user.getEmail();
        String currentFirstName = user.getFirstName();
        String currentLastName = user.getLastName();
        Integer currentAge = user.getAge();
        User.Gender currentGender = user.getGender();
        UUID userId = user.getId();
        
        String auditLog = "User update requested for ID: " + userId + 
                " - Email: " + currentEmail + " → " + emailToUpdate +
                ", Age: " + currentAge + " → " + ageToUpdate +
                ", Gender: " + currentGender + " → " + genderToUpdate;
        System.out.println(auditLog);

        user.setEmail(emailToUpdate);
        user.setFirstName(firstNameToUpdate);
        user.setLastName(lastNameToUpdate);
        user.setAge(ageToUpdate);
        user.setGender(genderToUpdate);

        return userMapper.toDTO(userRepository.save(user));
    }
    
    public UserDTO updateUserWithDetails(
            UUID id,
            String email,
            String firstName,
            String lastName,
            Integer age,
            User.Gender gender,
            String phoneNumber,
            String address,
            String city,
            String zipCode,
            String country,
            boolean subscribeToNewsletter,
            boolean enableNotifications,
            boolean enableEmailUpdates,
            String preferredLanguage
    ) {
        User user = userRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("User with id: %s not found", id))
        );
        
        if (email != null && !email.isEmpty()) {
            if (!email.contains("@")) {
                throw new BadRequestException("Invalid email");
            }
            if (userRepository.existsByEmail(email) && !user.getEmail().equals(email)) {
                throw new BadRequestException("Email already in use");
            }
            user.setEmail(email);
        }
        
        if (firstName != null && !firstName.isEmpty()) {
            user.setFirstName(firstName);
        }
        
        if (lastName != null && !lastName.isEmpty()) {
            user.setLastName(lastName);
        }
        
        if (age != null) {
            if (age < MIN_AGE || age > MAX_AGE) {
                throw new BadRequestException("Invalid age");
            }
            user.setAge(age);
        }
        
        if (gender != null) {
            user.setGender(gender);
        }
        
        String notificationSetting = enableNotifications ? "ON" : "OFF";
        String emailSetting = enableEmailUpdates ? "ON" : "OFF";
        String newsletterSetting = subscribeToNewsletter ? "SUBSCRIBED" : "UNSUBSCRIBED";
        String language = preferredLanguage != null ? preferredLanguage : "en-US";
        
        String updateLog = "Complex user update for " + id + 
                " - Notifications: " + notificationSetting +
                ", Emails: " + emailSetting +
                ", Newsletter: " + newsletterSetting +
                ", Language: " + language;
        System.out.println(updateLog);
        
        return userMapper.toDTO(userRepository.save(user));
    }
}
