package com.khartec.waltz.service.taxonomy_management.processors;

import com.khartec.waltz.common.DateTimeUtilities;
import com.khartec.waltz.model.Severity;
import com.khartec.waltz.model.measurable.Measurable;
import com.khartec.waltz.model.taxonomy_management.*;
import com.khartec.waltz.service.measurable.MeasurableService;
import com.khartec.waltz.service.measurable_rating.MeasurableRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.khartec.waltz.common.Checks.checkNotNull;
import static com.khartec.waltz.service.taxonomy_management.TaxonomyManagementUtilities.*;

@Service
public class UpdateMeasurableConcreteFlagCommandProcessor extends MeasurableFieldUpdateCommandProcessor {

    private static final Logger LOG = LoggerFactory.getLogger(UpdateMeasurableConcreteFlagCommandProcessor.class);

    private final MeasurableService measurableService;
    private final MeasurableRatingService measurableRatingService;


    @Autowired
    public UpdateMeasurableConcreteFlagCommandProcessor(MeasurableService measurableService,
                                                        MeasurableRatingService measurableRatingService) {
        checkNotNull(measurableService, "measurableService cannot be null");
        checkNotNull(measurableRatingService, "measurableRatingService cannot be null");
        this.measurableService = measurableService;
        this.measurableRatingService = measurableRatingService;
    }


    @Override
    public TaxonomyChangeType type() {
        return TaxonomyChangeType.UPDATE_CONCRETENESS;
    }


    public TaxonomyChangePreview preview(TaxonomyChangeCommand cmd) {
        doBasicValidation(cmd);
        Measurable m = validateMeasurable(measurableService, cmd);

        ImmutableTaxonomyChangePreview.Builder preview = ImmutableTaxonomyChangePreview
                .builder()
                .command(ImmutableTaxonomyChangeCommand
                        .copyOf(cmd)
                        .withA(m.entityReference()));

        boolean newValue = cmd.valueAsBoolean();

        if (hasNoChange(m, newValue)) {
            return preview.build();
        }

        if (newValue == false) {
            addToPreview(
                    preview,
                    findCurrentRatingMappings(measurableRatingService, cmd),
                    Severity.WARNING,
                    "Current app mappings exist to item, these will be invalid when the item becomes non-concrete");
        }

        return preview.build();
    }


    public TaxonomyChangeCommand apply(TaxonomyChangeCommand cmd, String userId) {
        doBasicValidation(cmd);
        validateMeasurable(measurableService, cmd);

        measurableService.updateConcreteFlag(
                cmd.a().id(),
                cmd.valueAsBoolean());

        return ImmutableTaxonomyChangeCommand
                .copyOf(cmd)
                .withExecutedAt(DateTimeUtilities.nowUtc())
                .withExecutedBy(userId)
                .withStatus(TaxonomyChangeLifecycleStatus.EXECUTED);
    }


    // --- helpers

    private boolean hasNoChange(Measurable m, boolean newValue) {
        boolean currentState = m.concrete();
        if (currentState == newValue) {
            LOG.info("Aborting command as nothing to do, concrete flag is already {}", newValue);
            return true;
        } else {
            return false;
        }
    }

}