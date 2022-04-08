
package at.jku.anttracks.gui.classification.filter;

import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.annotations.F;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.heap.space.SpaceType;

@F(name = "Only objects in OLD spaces",
        desc = "This filter removes every object that resides outside of OLD spaces",
        collection = ClassifierSourceCollection.ALL)
public class OnlyOldSpaceFilter extends Filter {

    @Override
    public Boolean classify() {
        return space().getType() == SpaceType.OLD;
    }

}
