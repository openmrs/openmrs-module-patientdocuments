SELECT
    name.given_name AS given_name,
    name.middle_name AS middle_name,
    name.family_name AS family_name,
    msf_id.identifier AS msf_identifier,
    legacy_id.identifier AS legacy_identifier,
    person.gender AS gender,
    person.birthdate AS birthdate,
    person.birthdate_estimated AS birthdate_estimated,
    address.city_village AS city,
    address.address1 AS address1,
    address.address2 AS address2,
    address.state_province AS state_province,
    address.country AS country,
    person.dead AS dead,
    person.death_date AS death_date,
    person.cause_of_death AS cause_of_death,
    person.creator AS creator,
    person.date_created AS date_created,
    person.voided AS person_voided,
    person.void_reason AS person_void_reason
FROM
    patient
    JOIN person ON patient.patient_id = person.person_id AND person.voided = 0
    JOIN person_name name ON person.person_id = name.person_id AND name.voided = 0
    LEFT JOIN person_address address ON person.person_id = address.person_id
    LEFT JOIN (
        SELECT patient_id, identifier 
        FROM patient_identifier 
        WHERE identifier_type = (
            SELECT patient_identifier_type_id 
            FROM patient_identifier_type 
            WHERE name = 'MSF ID'
        ) 
        AND voided = 0
    ) msf_id ON patient.patient_id = msf_id.patient_id
    LEFT JOIN (
        SELECT patient_id, identifier 
        FROM patient_identifier 
        WHERE identifier_type = (
            SELECT patient_identifier_type_id 
            FROM patient_identifier_type 
            WHERE name = 'Legacy MSF ID'
        ) 
        AND voided = 0
    ) legacy_id ON patient.patient_id = legacy_id.patient_id
WHERE 
    patient.voided = 0
    AND (
        (:patientNameOrID IS NULL)
        OR (msf_id.identifier LIKE CONCAT('%', :patientNameOrID, '%'))
        OR (legacy_id.identifier LIKE CONCAT('%', :patientNameOrID, '%'))
        OR (CONCAT_WS(' ', name.given_name, name.middle_name, name.family_name) 
            LIKE CONCAT('%', :patientNameOrID, '%'))
    );
