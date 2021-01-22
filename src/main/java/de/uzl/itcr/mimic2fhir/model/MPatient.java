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
package de.uzl.itcr.mimic2fhir.model;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.HumanName.NameUse;
import org.hl7.fhir.r4.model.Patient;

import ca.uhn.fhir.model.primitive.IdDt;

/**
 * FHIR-Patient with data from mimic3, one row in mimiciii.patients
 * @author Stefanie Ververs
 *
 */
public class MPatient {
	
	public MPatient() {
		admissions = new ArrayList<MAdmission>();
	}
	
	public void addAdmission(MAdmission adm) {
		admissions.add(adm);
	}
	
	private List<MAdmission> admissions;
	
	public List<MAdmission> getAdmissions() {
		return admissions;
	}

	private String patientSubjectId;
	public String getPatientSubjectId() {
		return patientSubjectId;
	}

	public void setPatientSubjectId(String patientSubjectId) {
		this.patientSubjectId = patientSubjectId;
	}

	public Date getBirthDate() {
		return birthDate;
	}

	public void setBirthDate(Date birthDate) {
		this.birthDate = birthDate;
	}

	public String getGender() {
		return gender;
	}

	public void setGender(String gender) {
		this.gender = gender;
	}

	public Date getDeathDate() {
		return deathDate;
	}

	public void setDeathDate(Date deathDate) {
		this.deathDate = deathDate;
	}

	private Date birthDate;
	private String gender;
	private Date deathDate;
	
	/**
	 * Create FHIR-"Patient"-resource from this data
	 * @return FHIR-Patient
	 */
	public Patient createFhirFromMimic() {
		Patient pMimic = new Patient();
		
		//ID:
		pMimic.addIdentifier().setSystem("http://www.imi-mimic.de/patients").setValue(patientSubjectId);

		//Name : Patient_ID
		pMimic.addName().setUse(NameUse.OFFICIAL).setFamily("Patient_" + patientSubjectId);
		
		//Date of Birth
		pMimic.setBirthDate(birthDate);
		
		//Date of Death
		if(deathDate != null) {
			pMimic.setDeceased(new DateTimeType(deathDate));
		}
		
		//Gender
		switch(gender) {
			case "M":
				pMimic.setGender(AdministrativeGender.MALE);
				break;
			case "F":
				pMimic.setGender(AdministrativeGender.FEMALE);
				break;
			default:
				pMimic.setGender(AdministrativeGender.UNKNOWN);
		}
		
		if(admissions.size() > 0) {
			//from first admission
			MAdmission firstAdm = admissions.get(0);
			
			//Marital Status - 
			CodeableConcept cc = new CodeableConcept();
			
			if(firstAdm.getMaritalStatus() != null){
				switch(firstAdm.getMaritalStatus()) {
					case "MARRIED":
						cc.addCoding().setCode("M").setSystem("http://hl7.org/fhir/v4/MaritalStatus").setDisplay("Married");
						break;
					case "SINGLE":
						cc.addCoding().setCode("S").setSystem("http://hl7.org/fhir/v4/MaritalStatus").setDisplay("Never Married");
							break;
					case "WIDOWED":
						cc.addCoding().setCode("W").setSystem("http://hl7.org/fhir/v4/MaritalStatus").setDisplay("Widowed");
							break;
					case "DIVORCED":
						cc.addCoding().setCode("D").setSystem("http://hl7.org/fhir/v4/MaritalStatus").setDisplay("Divorced");
						break;
					case "SEPARATED":
						cc.addCoding().setCode("L").setSystem("http://hl7.org/fhir/v4/MaritalStatus").setDisplay("Legally Separated");
						break;
					default:
						cc.addCoding().setCode("UNK").setSystem("http://hl7.org/fhir/v4/MaritalStatus").setDisplay("Unknown");
				}
				pMimic.setMaritalStatus(cc);	
			}
			
			//Language
			if(firstAdm.getLanguage() != null) {
				//Languages sometimes guessed - no dictionary or something in mimic..
				switch(firstAdm.getLanguage()) {
				case "*DUT":
					pMimic.setLanguage("nl");
					break;
				case "URDU":
				case "*URD":
					pMimic.setLanguage("ur");
					break;
				case "*NEP":
					pMimic.setLanguage("ne");
					break;
				case "TAGA":
					pMimic.setLanguage("tl");
					break;
				case "*TOY":
					pMimic.setLanguage("toy");
					break;
				case "*RUS":
				case "RUSS":
					pMimic.setLanguage("ru");
					break;
				case "ENGL":
					pMimic.setLanguage("en");
					break;
				case "*ARM":
					pMimic.setLanguage("hy");
					break;
				case "CANT":
					pMimic.setLanguage("yue");
					break;
				case "LAOT":
					pMimic.setLanguage("tyl");
					break;
				case "*MOR":
					pMimic.setLanguage("mor");
					break;
				case "*FUL":
					pMimic.setLanguage("ff");
					break;
				case "*ROM":
					pMimic.setLanguage("ro");
					break;
				case "*TOI":
					pMimic.setLanguage("toi");
					break;
				case "BENG":
				case "*BEN":
					pMimic.setLanguage("bn");
					break;
				case "**TO":
					pMimic.setLanguage("to");
					break;
				case "PERS":
				case "*PER":
					pMimic.setLanguage("fa");
					break;
				case "*TEL":
					pMimic.setLanguage("te");
					break;
				case "*YID":
					pMimic.setLanguage("ji");
					break;
				case "*CDI":
					pMimic.setLanguage("cdi");
					break;
				case "JAPA":
					pMimic.setLanguage("jp");
					break;
				case "ALBA":
					pMimic.setLanguage("sq");
					break;
				case "ARAB":
				case "*ARA":
					pMimic.setLanguage("ar");
					break;
				case "ITAL":
					pMimic.setLanguage("it");
					break;
				case "*TAM":
					pMimic.setLanguage("taq");
					break;
				case "*SPA":
				case "SPAN":
					pMimic.setLanguage("es");
					break;
				case "*BOS":
					pMimic.setLanguage("bs");
					break;
				case "*AMH":
					pMimic.setLanguage("am");
					break;
				case "SOMA":
					pMimic.setLanguage("so");
					break;
				case "CAPE":
					pMimic.setLanguage("cap");
					break;
				case "*PUN":
					pMimic.setLanguage("pa");
					break;
				case "POLI":
					pMimic.setLanguage("pl");
					break;
				case "*CHI":
					pMimic.setLanguage("zh");
					break;
				case "*BUR":
					pMimic.setLanguage("my");
					break;
				case "*CAN":
					pMimic.setLanguage("can");
					break;
				case "*YOR":
					pMimic.setLanguage("yox");
					break;
				case "*KHM":
				case "CAMB":
					pMimic.setLanguage("km");
					break;
				case "AMER":
					pMimic.setLanguage("en");
					break;
				case "*LIT":
					pMimic.setLanguage("lt");
					break;
				case "*IBO":
					pMimic.setLanguage("ibn");
					break;
				case "KORE":
					pMimic.setLanguage("ko");
					break;
				case "*FIL":
					pMimic.setLanguage("fil");
					break;
				case "THAI":
					pMimic.setLanguage("th");
					break;
				case "**SH":
					pMimic.setLanguage("sh");
					break;
				case "FREN":
					pMimic.setLanguage("fr");
					break;
				case "*FAR":
					pMimic.setLanguage("far");
					break;
				case "*CRE":
					pMimic.setLanguage("crp");
					break;
				case "HIND":
					pMimic.setLanguage("hi");
					break;
				case "*HUN":
					pMimic.setLanguage("hu");
					break;
				case "ETHI":
					pMimic.setLanguage("eth");
					break;
				case "VIET":
					pMimic.setLanguage("vi");
					break;
				case "*MAN":
					pMimic.setLanguage("man");
					break;
				case "GERM":
					pMimic.setLanguage("de");
					break;
				case "*PHI":
					pMimic.setLanguage("phi");
					break;
				case "TURK":
					pMimic.setLanguage("tr");
					break;
				case "*DEA":
					pMimic.setLanguage("mjl");
					break;
				case "PTUN":
					pMimic.setLanguage("ptu");
					break;
				case "GREE":
					pMimic.setLanguage("el");
					break;
				case "MAND":
					pMimic.setLanguage("cmn");
					break;
				case "HAIT":
					pMimic.setLanguage("ht");
					break;
				case "SERB":
					pMimic.setLanguage("sr");
					break;
				case "*BUL":
					pMimic.setLanguage("bg");
					break;
				case "*LEB":
					pMimic.setLanguage("leb");
					break;
				case "*GUJ":
					pMimic.setLanguage("gu");
					break;
				case "PORT":
					pMimic.setLanguage("pt");
					break;
				case "* BE":
					pMimic.setLanguage("be");
					break;
				default:
				    pMimic.setLanguage(firstAdm.getLanguage());
				}
			}
		}
		
		
		// Give the patient a temporary UUID so that other resources in
		// the transaction can refer to it
		pMimic.setId(IdDt.newRandomUuid());
		
		return pMimic;
	}

}
