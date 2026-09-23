package com.svivanrilski.svirerp.organization;

import com.svivanrilski.svirerp.common.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class OrganizationService {

    private final OrganizationRepository repo;

    public Organization getOrganization() {
        return repo.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new ResourceNotFoundException("No organization exists yet"));
    }

    @Transactional
    public Organization upsert(Organization patch) {
        Organization existing = repo.findFirstByOrderByCreatedAtAsc().orElseGet(Organization::new);
        existing.setName(patch.getName());
        existing.setLegalName(patch.getLegalName());
        existing.setTaxIdEin(patch.getTaxIdEin());
        existing.setNonprofitType(patch.getNonprofitType());
        existing.setMissionStatement(patch.getMissionStatement());
        existing.setAddressLine1(patch.getAddressLine1());
        existing.setAddressLine2(patch.getAddressLine2());
        existing.setCity(patch.getCity());
        existing.setState(patch.getState());
        existing.setZip(patch.getZip());
        existing.setCountry(patch.getCountry());
        existing.setPhone(patch.getPhone());
        existing.setEmail(patch.getEmail());
        existing.setWebsite(patch.getWebsite());
        existing.setFoundedDate(patch.getFoundedDate());
        existing.setFiscalYearStart(patch.getFiscalYearStart());
        existing.setLogoUrl(patch.getLogoUrl());
        return repo.save(existing);
    }
}
