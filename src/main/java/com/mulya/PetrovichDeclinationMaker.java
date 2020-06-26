package com.mulya;

import com.fasterxml.jackson.jr.ob.JSON;
import com.mulya.beans.NameBean;
import com.mulya.beans.RootBean;
import com.mulya.beans.RuleBean;
import com.mulya.enums.Case;
import com.mulya.enums.Gender;
import com.mulya.enums.NamePart;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: mulya
 * Date: 28/09/2014
 */
public class PetrovichDeclinationMaker {

	private static final String DEFAULT_PATH_TO_RULES_FILE = "rules.json";
	private static final String PATH_TO_MAN_NAMES = "firstnames-man.txt";
	private static final String PATH_TO_WOMAN_NAMES = "firstnames-woman.txt";
	private static final String MODS_KEEP_IT_ALL_SYMBOL = ".";
	private static final String MODS_REMOVE_LETTER_SYMBOL = "-";

	private RootBean rootRulesBean;

	private Set<String> manNames;
	private Set<String> womanNames;

	public GenderCurryedMaker male = new GenderCurryedMaker(Gender.MALE);
	public GenderCurryedMaker female = new GenderCurryedMaker(Gender.FEMALE);
	public GenderCurryedMaker androgynous = new GenderCurryedMaker(Gender.ANDROGYNOUS);


	private PetrovichDeclinationMaker(String pathToRulesFile) throws IOException {
		try(InputStream is =  getClass().getClassLoader().getResourceAsStream(DEFAULT_PATH_TO_RULES_FILE);
		) {
			rootRulesBean = JSON.std.beanFrom(RootBean.class, new String(IOUtils.toByteArray(is),
					StandardCharsets.UTF_8));
		}

		try(InputStream is =  getClass().getClassLoader().getResourceAsStream(PATH_TO_MAN_NAMES);
		) {
			List<String> names = IOUtils.readLines(is, StandardCharsets.UTF_8);
			manNames = names.stream().map(name-> name.toLowerCase()).collect(Collectors.toSet());
			System.out.println(manNames);
		}

		try(InputStream is =  getClass().getClassLoader().getResourceAsStream(PATH_TO_WOMAN_NAMES);
		) {
			List<String> names = IOUtils.readLines(is, StandardCharsets.UTF_8);
			womanNames = names.stream().map(name-> name.toLowerCase()).collect(Collectors.toSet());
		}


	}

	public static PetrovichDeclinationMaker getInstance() throws IOException {
		return getInstance(DEFAULT_PATH_TO_RULES_FILE);
	}

	public static PetrovichDeclinationMaker getInstance(String pathToRulesFile) throws IOException {
		return new PetrovichDeclinationMaker(pathToRulesFile);
	}

	public Gender tryToGuessGender(String firstname){
		if(womanNames.contains(firstname.toLowerCase())){
			return Gender.FEMALE;
		}else {
			return Gender.MALE;
		}
	}

	public String make(NamePart namePart, Gender gender, Case caseToUse, String originalName) {
		String result = originalName;
		NameBean nameBean;

		switch (namePart) {
			case FIRSTNAME:
				nameBean = rootRulesBean.getFirstname();
				break;
			case LASTNAME:
				nameBean = rootRulesBean.getLastname();
				break;
			case MIDDLENAME:
				nameBean = rootRulesBean.getMiddlename();
				break;
			default:
				nameBean = rootRulesBean.getMiddlename();
				break;
		}


		RuleBean ruleToUse = null;
		RuleBean exceptionRuleBean = findInRuleBeanList(nameBean.getExceptions(), gender, originalName);
		RuleBean suffixRuleBean = findInRuleBeanList(nameBean.getSuffixes(), gender, originalName);
		if (exceptionRuleBean != null && exceptionRuleBean.getGender().equals(gender.getValue())) {
			ruleToUse = exceptionRuleBean;
		} else if (suffixRuleBean != null && suffixRuleBean.getGender().equals(gender.getValue())) {
			ruleToUse = suffixRuleBean;
		} else {
			ruleToUse = exceptionRuleBean != null ? exceptionRuleBean : suffixRuleBean;
		}

		if (ruleToUse != null) {
			String modToApply = ruleToUse.getMods().get(caseToUse.getValue());
			result = applyModToName(modToApply, originalName);
		}

		return result;
	}

	protected String applyModToName(String modToApply, String name) {
		String result = name;
		if (!modToApply.equals(MODS_KEEP_IT_ALL_SYMBOL)) {
			if (modToApply.contains(MODS_REMOVE_LETTER_SYMBOL)) {
				for (int i = 0; i < modToApply.length(); i++) {
					if (Character.toString(modToApply.charAt(i)).equals(MODS_REMOVE_LETTER_SYMBOL)) {
						result = result.substring(0, result.length() - 1);
					} else {
						result += modToApply.substring(i);
						break;
					}
				}
			} else {
				result = name + modToApply;
			}
		}
		return result;
	}

	protected RuleBean findInRuleBeanList(List<RuleBean> ruleBeanList, Gender gender, String originalName) {
		RuleBean result = null;
		if (ruleBeanList != null) {
			out:
			for(RuleBean ruleBean : ruleBeanList) {
				for (String test : ruleBean.getTest()) {
					if (originalName.endsWith(test)) {
						if (ruleBean.getGender().equals(Gender.ANDROGYNOUS.getValue())) {
							result = ruleBean;
							break out;
						} else if ((ruleBean.getGender().equals(gender.getValue()))) {
							result = ruleBean;
							break out;
						}
					}
				}
			}
		}

		return result;
	}

	protected class GenderCurryedMaker {
		private Gender gender;

		protected GenderCurryedMaker(Gender gender) {
			this.gender = gender;
		}

		public GenderAndNamePartCurryedMaker firstname() {
			return new GenderAndNamePartCurryedMaker(gender, NamePart.FIRSTNAME);
		}

		public GenderAndNamePartCurryedMaker lastname() {
			return new GenderAndNamePartCurryedMaker(gender, NamePart.LASTNAME);
		}

		public GenderAndNamePartCurryedMaker middlename() {
			return new GenderAndNamePartCurryedMaker(gender, NamePart.MIDDLENAME);
		}
	}

	protected class GenderAndNamePartCurryedMaker {
		private NamePart namePart;
		private Gender gender;

		protected GenderAndNamePartCurryedMaker(Gender gender, NamePart namePart) {
			this.gender = gender;
			this.namePart = namePart;
		}

		public String toGenitive(String name) {
			return make(namePart, gender, Case.GENITIVE, name);
		}

		public String toDative(String name) {
			return make(namePart, gender, Case.DATIVE, name);
		}

		public String toAccusative(String name) {
			return make(namePart, gender, Case.ACCUSATIVE, name);
		}

		public String toInstrumental(String name) {
			return make(namePart, gender, Case.INSTRUMENTAL, name);
		}

		public String toPrepositional(String name) {
			return make(namePart, gender, Case.PREPOSITIONAL, name);
		}
	}
}
