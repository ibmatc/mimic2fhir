/***********************************************************************
Copyright 2018 Stefanie Ververs, University of Lübeck

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
/***********************************************************************/
package de.uzl.itcr.mimic2fhir;

import java.util.HashMap;
import java.util.List;

import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Medication;
import org.hl7.fhir.r4.model.MedicationStatement;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Practitioner;
import org.hl7.fhir.r4.model.PractitionerRole;
import org.hl7.fhir.r4.model.Procedure;
import org.hl7.fhir.r4.model.Reference;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.narrative.DefaultThymeleafNarrativeGenerator;
import de.uzl.itcr.mimic2fhir.model.MAdmission;
import de.uzl.itcr.mimic2fhir.model.MCaregiver;
import de.uzl.itcr.mimic2fhir.model.MPatient;
import de.uzl.itcr.mimic2fhir.model.MTransfer;
import de.uzl.itcr.mimic2fhir.model.MWard;
import de.uzl.itcr.mimic2fhir.work.BundleControl;
import de.uzl.itcr.mimic2fhir.work.Config;
import de.uzl.itcr.mimic2fhir.work.ConnectDB;

/**
 * Application for transforming data from mimiciii to fhir
 * 
 * @author Stefanie Ververs 
 * 
 * This class is a clone of Mimic2Fhir, but altered to allow iterating over the returned patient's fhir bundles.
 * This removes the requirement for a Rabbit MQ instance and a fhir server, but does not allow limiting the number of results, nor provide various output modes. 
 */
public class Mimic2Fhir2 {
	//Config-Object
	private Config config;
	private ConnectDB dbAccess;
	private HashMap<Integer,MCaregiver> caregivers;
	private FhirContext fhirContext;
	private HashMap<Integer,MWard> locations;

	private HashMap<String,String> locationsInBundle;
	private HashMap<String,String> caregiversInBundle;
	private HashMap<String,String> medicationInBundle;
	private Organization hospital;
	private BundleControl bundleC;
	private List<Integer> rowIds = null;

	
	public Config getConfig() {
		return config;
	}

	/**
	 * Set Configuration Object for App
	 * @param config Config-Object
	 */
	public void setConfig(Config config) {
		this.config = config;
	}

    /**
	 * Start transformation 
	 */
	public void start() {	
    	//Connection to mimic postgres DB
    	dbAccess = new ConnectDB(config);
    	
    	//Preload Caregivers
    	caregivers = dbAccess.getCaregivers();
    	
    	//Preload Wards
    	locations = dbAccess.getLocations();
    	
    	//initialize memoryLists of locations and caregivers and medication (-> conditional creates, each resource only once in bundle)
    	locationsInBundle = new HashMap<String,String>();
    	caregiversInBundle = new HashMap<String,String>();
    	medicationInBundle = new HashMap<String,String>();
    	
    	//Preload Hospital
    	hospital = createTopHospital();
    	
    	bundleC = new BundleControl();

        // loop all patients..
        rowIds = dbAccess.getPatientRowIds();
    }

	private void resetMemoryLists() {
		caregiversInBundle.clear();
		locationsInBundle.clear();
		medicationInBundle.clear();
	}
	
	public synchronized Integer getNextPatientId() {
	    return rowIds.remove(0);
	}

    public String getNextPatient() {
        return getPatient(getNextPatientId());
    }
	
    public List<Integer> getPatientIds() {
        return rowIds;
    }
    
	public String getPatient(Integer patientId) {
        MPatient mimicPat = null;
        if (rowIds != null && !rowIds.isEmpty()) {
            mimicPat = dbAccess.getPatientByRowId(patientId);
        }
        if (mimicPat == null) {
            return null;
        }
        try {
            // Fill FHIR-Structure
            Patient fhirPat = mimicPat.createFhirFromMimic();

            // All admissions of one patient
            for (MAdmission admission : mimicPat.getAdmissions()) {

                // First: Load/create fhir resources
                Encounter enc = admission.createFhirEncounterFromMimic(fhirPat.getId());

                // create Conditions per Admission
                List<Condition> conditions = admission.createFhirConditionsFromMimic(fhirPat.getId());

                // create Procedures per Admission
                List<Procedure> procedures = admission.createFhirProceduresFromMimic(fhirPat.getId());

                // create List Of Medication & MedicationAdministrations
                List<Medication> medications = admission.createFhirMedicationsFromMimic();
                List<MedicationStatement> prescriptions = admission.createFhirMedicationStatementsFromMimic(fhirPat.getId(), enc.getId());

                // create Observations per Admission
                List<Observation> obs = admission.createFhirObservationsFromMimic(fhirPat.getId(), enc.getId());
                // create Observation from Labevents
                List<Observation> obsLab = admission.createFhirLabObservationsFromMimic(fhirPat.getId(), enc.getId());
                // create Observation from Noteevents
                List<Observation> obsNotes = admission.createFhirNoteObservationsFromMimic(fhirPat.getId(), enc.getId());

                // create bundle without observations and medication:
                createBasicBundle(fhirPat, admission, enc, conditions, procedures);

                // Medication only in first bundle of admission
                // Prescriptions
                for (Medication med : medications) {
                    String identifier = med.getCode().getCodingFirstRep().getCode();
                    if (!medicationInBundle.containsKey(identifier)) {
                        bundleC.addUUIDResourceWithConditionToBundle(med, "code=" + med.getCode().getCodingFirstRep().getCode());
                        medicationInBundle.put(identifier, med.getId());
                    }
                }

                // ..and MedicationAdministrations (with correct Medication as Reference)
                for (MedicationStatement mstmt : prescriptions) {
                    String identifier = medications.get(prescriptions.indexOf(mstmt)).getCode().getCodingFirstRep().getCode();
                    String medId = medicationInBundle.get(identifier);
                    mstmt.setMedication(new Reference(medId));

                    bundleC.addUUIDResourceToBundle(mstmt);
                }

                // add observations to bundle
                for (Observation o : obs) {
                    // get Caregiver for this event
                    int caregiverId = admission.getEvents().get(obs.indexOf(o)).getCareGiverId();
                    if (caregiverId != 0) {
                        String pFhirId = processCaregiver(caregiverId);

                        // Set caregiver-Reference -> Performer
                        o.addPerformer(new Reference(pFhirId));
                    }
                    // Order important - these reference pat & encounter
                    bundleC.addResourceToBundle(o);
                }

                for (Observation o : obsLab) {
                    bundleC.addResourceToBundle(o);
                }

                for (Observation o : obsNotes) {
                    // get Caregiver for this event
                    int caregiverId = admission.getNoteevents().get(obsNotes.indexOf(o)).getCaregiverId();
                    if (caregiverId != 0) {
                        String pFhirId = processCaregiver(caregiverId);

                        // Set caregiver-Reference -> Performer
                        o.addPerformer(new Reference(pFhirId));
                    }

                    bundleC.addResourceToBundle(o);
                }
            }

            if (fhirContext == null) {
                fhirContext = FhirContext.forR4();
                // Use the narrative generator
                fhirContext.setNarrativeGenerator(new DefaultThymeleafNarrativeGenerator());
            }
            String jsonBundle = fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToString(bundleC.getTransactionBundle());

            return jsonBundle;
        } finally {
            // reset bundle and memory lists
            bundleC.resetBundle();
            resetMemoryLists();
            bundleC.resetInternalBundleNumber();
        }
    }

	
    private void createBasicBundle(Patient fhirPat, MAdmission admission, Encounter enc, List<Condition> conditions, 
			List<Procedure> procedures) {
		
		//Pat to bundle
		bundleC.addUUIDResourceWithConditionToBundle(fhirPat, "identifier=" + fhirPat.getIdentifierFirstRep().getSystem() + "|" + fhirPat.getIdentifierFirstRep().getValue());
		
		//Top of all: Hospital
		bundleC.addUUIDResourceWithConditionToBundle(hospital, "identifier=" + hospital.getIdentifierFirstRep().getSystem() + "|" + hospital.getIdentifierFirstRep().getValue());
					
		enc.getDiagnosis().clear(); //clear all procedures & diagnoses
		
		//Diagnoses
		for(Condition c : conditions) {
			int rank = admission.getDiagnoses().get(conditions.indexOf(c)).getSeqNumber();
			
			//set Condition in enc.diagnosis
			enc.addDiagnosis().setCondition(new Reference(c.getId())).setRank(rank);
			
			//add Condition to bundle
			bundleC.addUUIDResourceWithConditionToBundle(c, "identifier=" + c.getIdentifierFirstRep().getSystem() + "|" + c.getIdentifierFirstRep().getValue());
		}
		
		//Procedures		
		for(Procedure p : procedures) {
			int rank = admission.getProcedures().get(procedures.indexOf(p)).getSeqNumber();
			
			//set Procedure in enc.diagnosis
			enc.addDiagnosis().setCondition(new Reference(p.getId())).setRank(rank);
			
			//add Procedure to bundle
			bundleC.addUUIDResourceWithConditionToBundle(p, "identifier=" + p.getIdentifierFirstRep().getSystem() + "|" + p.getIdentifierFirstRep().getValue());
		}
		
		//create transfer chain
		
		enc.getLocation().clear(); //clear all locations -> to be newly added
		
		for(MTransfer t : admission.getTransfers()) {
			Location locWard = locations.get(t.getCurrWard()).getFhirLocation();
			String identifier = locWard.getIdentifierFirstRep().getValue();
			String id;
			if(!locationsInBundle.containsKey(identifier)) {
				//add to memory list:
				locationsInBundle.put(identifier, locWard.getId());
				id =  locWard.getId();
				//Location: Set Hospital als TOp-Orga
				locWard.setManagingOrganization(new Reference(hospital.getId()));
				
				bundleC.addUUIDResourceWithConditionToBundle(locWard, "identifier=" + locWard.getIdentifierFirstRep().getSystem() + "|" + identifier);
			}else {
				id = locationsInBundle.get(identifier);
			}

			//Ward as Location
			enc.addLocation().setLocation(new Reference(id)).setPeriod(new Period().setStart(t.getIntime()).setEnd(t.getOuttime()));
		}
		
		//add Encounter to bundle
		bundleC.addUUIDResourceWithConditionToBundle(enc,"identifier=" + enc.getIdentifierFirstRep().getSystem() + "|" + enc.getIdentifierFirstRep().getValue());
	}

	private String processCaregiver(int caregiverId) {
		MCaregiver cgHere = caregivers.get(caregiverId);
		//Create FHIR-Resources for Practitioner und -Role
		Practitioner pFhir = cgHere.getFhirRepresentation();
		String identifier = pFhir.getIdentifierFirstRep().getValue();
		String id;
		if(!caregiversInBundle.containsKey(identifier)) {
			//add to memory list
			caregiversInBundle.put(identifier, pFhir.getId());
			id = pFhir.getId();
			bundleC.addUUIDResourceWithConditionToBundle(pFhir, "identifier=" + pFhir.getIdentifierFirstRep().getSystem() + "|" + identifier);
			
			PractitionerRole roleFhir = cgHere.getFhirRepresentationRole();
			if(roleFhir != null) {
				roleFhir.setPractitioner(new Reference(pFhir.getId()));
				roleFhir.setOrganization(new Reference(hospital.getId()));
				bundleC.addUUIDResourceWithConditionToBundle(roleFhir, "identifier=" + roleFhir.getIdentifierFirstRep().getSystem() + "|" + roleFhir.getIdentifierFirstRep().getValue());
			}
		}
		else {
			id = caregiversInBundle.get(identifier);
		}
		return id;
	}
	
	private Organization createTopHospital() {
		//Create a "dummy" Organization that is "top player" of PractitionerRoles and Locations
		Organization hospital = new Organization();
		
		hospital.addIdentifier().setSystem("http://www.imi-mimic.de").setValue("hospital");
		
		hospital.addType().addCoding().setCode("prov").setSystem("http://hl7.org/fhir/organization-type").setDisplay("Healthcare Provider");
		hospital.setName("IMI-Mimic Hospital");
		
		
		hospital.setId(IdDt.newRandomUuid());
		return hospital;
	}
}