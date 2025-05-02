package com.patientmanagement.patient_service.service;

import com.patientmanagement.patient_service.dto.PatientRequestDTO;
import com.patientmanagement.patient_service.dto.PatientResponseDTO;
import com.patientmanagement.patient_service.exception.EmailAlreadyExistsException;
import com.patientmanagement.patient_service.exception.PatientNotFoundException;
import com.patientmanagement.patient_service.grpc.BillingServiceGrpcClient;
import com.patientmanagement.patient_service.kafka.KafkaProducer;
import com.patientmanagement.patient_service.mapper.PatientMapper;
import com.patientmanagement.patient_service.model.Patient;
import com.patientmanagement.patient_service.repository.PatientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;


@Service
public class PatientService {

    private static final Logger log = LoggerFactory.getLogger(PatientService.class);
    private final PatientRepository patientRepository;
    private final BillingServiceGrpcClient billingServiceGrpcClient;
    private final KafkaProducer kafkaProducer;

    public PatientService(PatientRepository patientRepository, BillingServiceGrpcClient billingServiceGrpcClient, KafkaProducer kafkaProducer) {
        this.patientRepository = patientRepository;
        this.billingServiceGrpcClient = billingServiceGrpcClient;
        this.kafkaProducer = kafkaProducer;
    }
    // GET
    public List<PatientResponseDTO> getPatients() {
        // Find the actual patients
        List<Patient> patients= patientRepository.findAll();
        // convert to dtos and return
        return patients.stream().map(patient -> PatientMapper.toDTO(patient)).toList();
    }
    // POST
    public PatientResponseDTO createPatent(PatientRequestDTO patientRequestDTO) {
        // if email already exists, we can't create a new patient
        if (patientRepository.existsByEmail(patientRequestDTO.getEmail())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }


        // save the actual Patient in repo
        Patient newPatient = patientRepository.save(PatientMapper.toModel(patientRequestDTO));
        log.info(" New patient created: {}", newPatient.toString());
        // create a billing account for the new patient -> this is a gRPC call
        billingServiceGrpcClient.createBillingAccount(newPatient.getId().toString(), newPatient.getName(), newPatient.getEmail());

        kafkaProducer.sendEvent(newPatient);

        // return the DTO as response
        return PatientMapper.toDTO(newPatient);
    }
    // PUT
    public PatientResponseDTO updatePatient(UUID id, PatientRequestDTO patientRequestDTO) {
        // find the patient by id
        Patient patient = patientRepository.findById(id).orElseThrow(() -> new PatientNotFoundException("Patient not found with ID: " + id));

        if (patientRepository.existsByEmailAndIdNot(patientRequestDTO.getEmail(), id)) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        // update the patient
        patient.setName(patientRequestDTO.getName());
        patient.setAddress(patientRequestDTO.getAddress());
        patient.setEmail(patientRequestDTO.getEmail());
        patient.setDateOfBirth(LocalDate.parse(patientRequestDTO.getDateOfBirth()));
        patient.setAddress(patientRequestDTO.getAddress());

        Patient updatedPatient = patientRepository.save(patient);
        return PatientMapper.toDTO(updatedPatient);

    }
    //DELETE
    public void deletePatient(UUID id) {
        patientRepository.deleteById(id);
    }

}
