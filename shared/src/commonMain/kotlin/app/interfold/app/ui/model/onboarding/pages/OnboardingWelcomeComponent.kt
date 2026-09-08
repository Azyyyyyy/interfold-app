package app.interfold.app.ui.model.onboarding.pages

import app.interfold.app.ui.model.CommonComponentContext

interface OnboardingWelcomeComponent

class OnboardingWelcomeComponentImpl(
  componentContext: CommonComponentContext
) : OnboardingWelcomeComponent, CommonComponentContext by componentContext