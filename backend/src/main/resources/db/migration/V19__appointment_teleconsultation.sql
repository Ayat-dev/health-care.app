-- V19: Télémédecine légère (P3.7). Un rendez-vous de type TELECONSULTATION porte un
-- identifiant de salle visio unique et non devinable ; le lien de jonction est construit
-- à partir de app.telemedicine.base-url (Jitsi Meet par défaut — sans compte, sans IA).
-- Compatible PostgreSQL (prod) ET H2 (dev/test).

ALTER TABLE appointments ADD COLUMN teleconsultation_room VARCHAR(64);
