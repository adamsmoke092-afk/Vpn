import re

with open('app/src/main/java/com/unitytunnel/app/MainActivity.kt', 'r') as f:
    content = f.read()

double_up_old = """                                    // Watch second ad
                                    rewardedAdService.loadAd(onLoaded = {
                                        rewardedAdService.showAdForDoubleUp(onClosed = {
                                            viewModel.dismissDoubleUpOffer()
                                        })
                                    })"""
double_up_new = """                                    // Watch second ad
                                    viewModel.showRewardedAd(activity, "DOUBLE_UP") { success ->
                                        if (success) {
                                            viewModel.dismissDoubleUpOffer()
                                        } else {
                                            Toast.makeText(context, "Ad not ready or failed to show. Try again shortly.", Toast.LENGTH_SHORT).show()
                                        }
                                    }"""

content = content.replace(double_up_old, double_up_new)

with open('app/src/main/java/com/unitytunnel/app/MainActivity.kt', 'w') as f:
    f.write(content)
