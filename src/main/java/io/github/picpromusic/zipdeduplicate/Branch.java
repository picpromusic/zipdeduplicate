package io.github.picpromusic.zipdeduplicate;

import org.eclipse.jgit.transport.RefSpec;

public class Branch {
	
	private static final String REFS_DATA_PREFIX = "refs/heads/data_";
	private static final String REFS_DESC_PREFIX = "refs/heads/desc_";
	private static final String DATA_PREFIX = "refs/heads/data_";
	private static final String DESC_PREFIX = "refs/heads/desc_";

	private String branch;
	private String descBranchAsRef;
	private String dataBranchAsRef;
	private String descBranch;
	private String dataBranch;

	public Branch(String branch) {
		this.descBranchAsRef = REFS_DESC_PREFIX + branch;
		this.dataBranchAsRef = REFS_DATA_PREFIX + branch;
		this.descBranch = DESC_PREFIX + branch;
		this.dataBranch = DATA_PREFIX + branch;
		this.branch = branch;
	}

	public String getBranch() {
		return branch;
	}
	
	public String getDescBranchAsRef() {
		return descBranchAsRef;
	}

	public String getDataBranchAsRef() {
		return dataBranchAsRef;
	}

	
	public String getDescBranch() {
		return descBranch;
	}
	
	public String getDataBranch() {
		return dataBranch;
	}
	
	public RefSpec getFetchDescRefSpec() {
		return new RefSpec(descBranchAsRef + ":" + descBranch);
	}
	public RefSpec getFetchDataRefSpec() {
		return new RefSpec(dataBranchAsRef + ":" + dataBranch);
	}
}
